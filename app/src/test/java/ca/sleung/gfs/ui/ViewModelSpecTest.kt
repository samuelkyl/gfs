package ca.sleung.gfs.ui

import androidx.compose.ui.graphics.Color
import ca.sleung.gfs.MainViewModel
import ca.sleung.gfs.data.local.preferences.BudgetPreferencesRepository
import ca.sleung.gfs.data.repository.BudgetRepository
import ca.sleung.gfs.data.repository.FakeTagDao
import ca.sleung.gfs.data.repository.FakeTransactionDao
import ca.sleung.gfs.data.repository.InMemoryPreferencesDataStore
import ca.sleung.gfs.domain.engine.CycleCalculationEngine
import ca.sleung.gfs.ui.screens.dashboard.DashboardViewModel
import ca.sleung.gfs.ui.screens.history.HistoryViewModel
import ca.sleung.gfs.ui.screens.onboarding.OnboardingViewModel
import ca.sleung.gfs.ui.screens.settings.SettingsViewModel
import ca.sleung.gfs.ui.theme.NeutralSlateGray
import ca.sleung.gfs.ui.theme.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelSpecTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var preferencesRepository: BudgetPreferencesRepository
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var tagDao: FakeTagDao
    private lateinit var repository: BudgetRepository
    private var currentDate: LocalDate = LocalDate.of(2026, 10, 15)

    @Before
    fun setUp() {
        currentDate = LocalDate.of(2026, 10, 15)
        Dispatchers.setMain(testDispatcher)
        preferencesRepository = BudgetPreferencesRepository(InMemoryPreferencesDataStore())
        transactionDao = FakeTransactionDao()
        tagDao = FakeTagDao()
        repository = BudgetRepository(
            transactionDao = transactionDao,
            tagDao = tagDao,
            preferencesRepository = preferencesRepository,
            cycleEngine = CycleCalculationEngine(),
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
            currentDateProvider = { currentDate }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onboardingAndMainViewModel_completeOnboarding_updatesNavigationAndResumeRefresh() = runTest(testDispatcher) {
        val mainVm = MainViewModel(preferencesRepository, repository)
        val onboardingVm = OnboardingViewModel(repository)

        val collectJob = backgroundScope.launch(testDispatcher) {
            mainVm.hasCompletedOnboarding.collect()
        }

        assertFalse(mainVm.hasCompletedOnboarding.value ?: true)

        var callbackInvoked = false
        onboardingVm.completeOnboarding(750.0) {
            callbackInvoked = true
        }

        assertTrue(callbackInvoked)
        assertEquals(true, mainVm.hasCompletedOnboarding.value)

        // Verify onAppResumed propagates date refresh cleanly
        val dashVm = DashboardViewModel(repository)
        val dashJob = backgroundScope.launch(testDispatcher) { dashVm.uiState.collect() }
        assertEquals("2026-10", dashVm.uiState.value.budgetInfo.cycleKey)

        currentDate = LocalDate.of(2026, 11, 2)
        mainVm.onAppResumed()
        assertEquals("2026-11", dashVm.uiState.value.budgetInfo.cycleKey)

        dashJob.cancel()
        collectJob.cancel()
    }

    @Test
    fun dashboardViewModel_addExpenseAndLazyPagination_loads8Then16Items() = runTest(testDispatcher) {
        repository.completeOnboarding(500.0)
        val vm = DashboardViewModel(repository)

        val collectJob = backgroundScope.launch(testDispatcher) {
            vm.uiState.collect()
        }

        vm.openAddExpenseSheet()
        assertTrue(vm.uiState.value.showAddExpenseSheet)

        for (i in 1..12) {
            vm.addExpense(amount = 10.0, description = "Item $i", tag = "Eating Out", tagColor = "#FF7043")
        }

        // Sheet closes after adding expense and initial page size is 8
        assertFalse(vm.uiState.value.showAddExpenseSheet)
        assertEquals(8, vm.uiState.value.recentTransactions.size)
        assertEquals(380.0, vm.uiState.value.budgetInfo.remainingBalance, 0.001)

        // Trigger lazy load for next page
        vm.loadMoreTransactions()
        assertEquals(12, vm.uiState.value.recentTransactions.size)

        collectJob.cancel()
    }

    @Test
    fun historyViewModel_cycleFilterEditAndDelete_deduplicatesAndSortsHistoricalCycles() = runTest(testDispatcher) {
        repository.completeOnboarding(500.0)
        val augTimestamp = LocalDate.of(2026, 8, 10)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sepTimestamp = LocalDate.of(2026, 9, 10)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val octTimestamp = LocalDate.of(2026, 10, 10)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        repository.addExpense(amount = 30.0, description = "Aug Movie", timestamp = augTimestamp)
        repository.addExpense(amount = 80.0, description = "Sep Dinner", timestamp = sepTimestamp, tag = "Eating Out", tagColor = "#FF7043")
        repository.addExpense(amount = 50.0, description = "Oct Coffee", timestamp = octTimestamp, tag = "Coffee", tagColor = "#26A69A")

        val vm = HistoryViewModel(repository)
        val collectJob = backgroundScope.launch(testDispatcher) {
            vm.uiState.collect()
        }

        // Default cycle is current cycle (2026-10); availableCycles excludes 2026-10 and sorts newest-first
        assertEquals("2026-10", vm.uiState.value.currentCycleKey)
        assertEquals(listOf("2026-09", "2026-08"), vm.uiState.value.availableCycles)
        assertEquals(1, vm.uiState.value.transactions.size)
        assertEquals("Oct Coffee", vm.uiState.value.transactions.single().description)
        assertEquals(50.0, vm.uiState.value.totalSpent, 0.001)

        // Switch to All Time
        vm.selectCycle("ALL")
        assertEquals(3, vm.uiState.value.transactions.size)
        assertEquals(160.0, vm.uiState.value.totalSpent, 0.001)

        // Select and update a transaction
        val targetTx = vm.uiState.value.transactions.first { it.description == "Oct Coffee" }
        vm.selectTransaction(targetTx)
        assertEquals(targetTx.id, vm.uiState.value.selectedTransaction?.id)

        vm.updateExpense(
            id = targetTx.id,
            amount = 65.0,
            description = "Oct Specialty Coffee",
            timestamp = octTimestamp,
            tag = "Coffee",
            tagColor = "#26A69A"
        )
        assertNull(vm.uiState.value.selectedTransaction)
        assertEquals(175.0, vm.uiState.value.totalSpent, 0.001)

        // Delete transaction
        vm.deleteExpense(targetTx.id)
        assertEquals(2, vm.uiState.value.transactions.size)
        assertEquals(110.0, vm.uiState.value.totalSpent, 0.001)

        collectJob.cancel()
    }

    @Test
    fun settingsViewModel_saveSettingsTagsAndBackup_validatesAndTracksRestoreGeneration() = runTest(testDispatcher) {
        repository.completeOnboarding(500.0)
        val vm = SettingsViewModel(repository)

        val collectJob = backgroundScope.launch(testDispatcher) {
            vm.uiState.collect()
        }

        // Invalid settings are rejected before touching repository
        vm.saveSettings(monthlyBudget = 0.0, refillDay = 15, maxCap = 1000.0)
        assertEquals("Please enter valid budget settings", vm.uiState.value.statusMessage)
        assertEquals(500.0, vm.uiState.value.monthlyBudget, 0.001)

        vm.saveSettings(monthlyBudget = 800.0, refillDay = 15, maxCap = 1600.0)
        assertEquals(800.0, vm.uiState.value.monthlyBudget, 0.001)
        assertEquals(15, vm.uiState.value.refillDayOfMonth)
        assertEquals("Settings saved successfully", vm.uiState.value.statusMessage)

        vm.clearStatusMessage()
        assertNull(vm.uiState.value.statusMessage)

        vm.addTag("Books", "#42A5F5")
        assertEquals(1, vm.uiState.value.tags.size)
        assertEquals("Books", vm.uiState.value.tags.single().name)

        // Duplicate tag emits helpful status message
        vm.addTag("books", "#FF7043")
        assertEquals("Tag \"books\" already exists", vm.uiState.value.statusMessage)
        assertEquals(1, vm.uiState.value.tags.size)

        val genBefore = vm.uiState.value.restoreGeneration
        val exported = vm.getExportJson()
        
        // Stage, cancel, and confirm import
        vm.stageImportBackup(exported)
        assertTrue(vm.uiState.value.hasPendingImport)
        vm.cancelImportBackup()
        assertFalse(vm.uiState.value.hasPendingImport)

        vm.stageImportBackup(exported)
        assertTrue(vm.uiState.value.hasPendingImport)
        vm.confirmImportBackup()
        assertFalse(vm.uiState.value.hasPendingImport)
        assertTrue(vm.uiState.value.statusMessage?.contains("Restored") == true)
        assertEquals(genBefore + 1, vm.uiState.value.restoreGeneration)

        // Hide decimal places toggle
        assertTrue(vm.uiState.value.hideDecimalPlaces)
        vm.setHideDecimalPlaces(false)
        assertFalse(vm.uiState.value.hideDecimalPlaces)
        vm.setHideDecimalPlaces(true)
        assertTrue(vm.uiState.value.hideDecimalPlaces)

        vm.importBackup("invalid json")
        assertEquals("Failed to import backup: invalid file format", vm.uiState.value.statusMessage)

        collectJob.cancel()
    }

    @Test
    fun colorUtils_parseHexColor_handlesValidAndMalformedHexStrings() {
        assertEquals(Color(0xFFFF7043), parseHexColor("#FF7043"))
        assertEquals(Color(0xFFFF7043), parseHexColor("FF7043"))
        assertEquals(Color(0x80FF7043), parseHexColor("#80FF7043"))
        assertEquals(NeutralSlateGray, parseHexColor("not-a-color"))
        assertEquals(NeutralSlateGray, parseHexColor("#123"))
        assertEquals(NeutralSlateGray, parseHexColor(""))
    }
}
