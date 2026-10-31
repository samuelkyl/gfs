package ca.sleung.gfs.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import ca.sleung.gfs.data.local.db.DatabaseTransactionRunner
import ca.sleung.gfs.data.local.db.dao.TagDao
import ca.sleung.gfs.data.local.db.dao.TransactionDao
import ca.sleung.gfs.data.local.db.entity.TagEntity
import ca.sleung.gfs.data.local.db.entity.TransactionEntity
import ca.sleung.gfs.data.local.preferences.BudgetPreferencesRepository
import ca.sleung.gfs.domain.engine.CycleCalculationEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * End-to-end specification and data-integrity tests verifying all core business rules,
 * backup security invariants, and repository flows defined in docs/design/spec.md.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetRepositorySpecTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dataStore: InMemoryPreferencesDataStore
    private lateinit var preferencesRepository: BudgetPreferencesRepository
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var tagDao: FakeTagDao
    private lateinit var cycleEngine: CycleCalculationEngine
    private lateinit var repository: BudgetRepository

    private var currentDate: LocalDate = LocalDate.of(2026, 9, 10)
    private var atomicTransactionCount: Int = 0

    @Before
    fun setUp() {
        currentDate = LocalDate.of(2026, 9, 10)
        atomicTransactionCount = 0
        dataStore = InMemoryPreferencesDataStore()
        preferencesRepository = BudgetPreferencesRepository(dataStore)
        transactionDao = FakeTransactionDao()
        tagDao = FakeTagDao()
        cycleEngine = CycleCalculationEngine()
        repository = BudgetRepository(
            transactionDao = transactionDao,
            tagDao = tagDao,
            preferencesRepository = preferencesRepository,
            cycleEngine = cycleEngine,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher,
            transactionRunner = DatabaseTransactionRunner { block ->
                atomicTransactionCount++
                block()
            },
            currentDateProvider = { currentDate }
        )
    }

    private fun epochMillisFor(year: Int, month: Int, day: Int): Long {
        return LocalDate.of(year, month, day)
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    // Spec 2.1 & 3.5: First Launch Setup (1-Question Frictionless Onboarding)
    @Test
    fun spec_3_5_onboarding_setsSmartDefaultsAndFullStartingBalance() = runTest(testDispatcher) {
        repository.completeOnboarding(monthlyBudget = 600.0)

        val info = repository.budgetInfoFlow.first()
        assertTrue(info.hasCompletedOnboarding)
        assertEquals(600.0, info.monthlyBudget, 0.001)
        assertEquals(1, info.refillDayOfMonth)
        assertEquals(1200.0, info.maxRolloverCap, 0.001) // 2x monthly budget default
        assertEquals(0.0, info.rolloverAmount, 0.001)
        assertEquals(600.0, info.totalCycleBudget, 0.001)
        assertEquals(600.0, info.remainingBalance, 0.001) // Full starting balance, no pro-rating
        assertEquals(1.0f, info.remainingPercentage, 0.001f)
        assertEquals("2026-09", info.cycleKey)
    }

    // Spec 2.2: Rollover of unspent budget into next cycle
    @Test
    fun spec_2_2_positiveRollover_rollsOverUnspentBudgetIntoNextMonth() = runTest(testDispatcher) {
        repository.completeOnboarding(monthlyBudget = 500.0)

        // Spend $180 in September 2026 -> $320 remaining
        repository.addExpense(
            amount = 180.0,
            description = "Dinner with friends",
            timestamp = epochMillisFor(2026, 9, 12)
        )

        val septInfo = repository.budgetInfoFlow.first()
        assertEquals(320.0, septInfo.remainingBalance, 0.001)

        // Roll over to October 2026
        currentDate = LocalDate.of(2026, 10, 2)
        val octInfo = repository.budgetInfoFlow.first()

        assertEquals("2026-10", octInfo.cycleKey)
        assertEquals(320.0, octInfo.rolloverAmount, 0.001)
        assertEquals(820.0, octInfo.totalCycleBudget, 0.001) // $500 + $320 rollover
        assertEquals(820.0, octInfo.remainingBalance, 0.001)
        assertEquals(0.0, octInfo.totalExtraSaved, 0.001)
    }

    // Spec 2.2 & 3.4: Maximum Rollover Cap & Extra Saved overflow tracking + Reset Extra Saved
    @Test
    fun spec_2_2_maxRolloverCap_capsActiveRolloverAndTracksTotalExtraSaved() = runTest(testDispatcher) {
        repository.completeOnboarding(monthlyBudget = 500.0) // maxRolloverCap = 1000.0

        // Month 1 (2026-09): Spend $0 -> $500 unspent
        // Month 2 (2026-10): $500 + $500 rollover = $1000 -> Spend $0 -> $1000 unspent
        // Month 3 (2026-11): $500 + $1000 rollover = $1500 -> Spend $200 -> $1300 unspent
        repository.addExpense(
            amount = 200.0,
            description = "Weekend trip",
            timestamp = epochMillisFor(2026, 11, 10)
        )

        // Roll over to Month 4 (2026-12): $1300 remaining from 2026-11 exceeds $1000 cap by $300
        currentDate = LocalDate.of(2026, 12, 1)
        repository.reconcileRollover()
        val decInfo = repository.budgetInfoFlow.first()

        assertEquals("2026-12", decInfo.cycleKey)
        assertEquals(1000.0, decInfo.rolloverAmount, 0.001) // Capped at $1000
        assertEquals(1500.0, decInfo.totalCycleBudget, 0.001) // $500 + $1000 capped rollover
        assertEquals(300.0, decInfo.totalExtraSaved, 0.001) // $300 overflow tracked in Extra Saved

        // Reset Extra Saved in Settings
        repository.resetExtraSaved()
        val afterResetInfo = repository.budgetInfoFlow.first()
        assertEquals(0.0, afterResetInfo.totalExtraSaved, 0.001)
        assertEquals(1000.0, afterResetInfo.rolloverAmount, 0.001) // Rollover preserved
    }

    // Spec 2.3: Overspending Guard & Deficit Rollover to subsequent month
    @Test
    fun spec_2_3_overspendingDeficit_carriesOverAndDeductsFromNextMonthRefill() = runTest(testDispatcher) {
        repository.completeOnboarding(monthlyBudget = 500.0)

        // Spend $640 in September 2026 -> -$140 negative balance
        repository.addExpense(
            amount = 640.0,
            description = "Concert tickets",
            timestamp = epochMillisFor(2026, 9, 20)
        )

        val septInfo = repository.budgetInfoFlow.first()
        assertEquals(-140.0, septInfo.remainingBalance, 0.001)
        assertEquals(0.0f, septInfo.remainingPercentage, 0.001f)

        // Advance to October 2026: -$140 deficit must be deducted from October's $500 refill
        currentDate = LocalDate.of(2026, 10, 1)
        repository.reconcileRollover()
        val octInfo = repository.budgetInfoFlow.first()

        assertEquals("2026-10", octInfo.cycleKey)
        assertEquals(-140.0, octInfo.rolloverAmount, 0.001)
        assertEquals(360.0, octInfo.totalCycleBudget, 0.001) // $500 - $140 = $360 available
        assertEquals(360.0, octInfo.remainingBalance, 0.001)
        assertEquals(1.0f, octInfo.remainingPercentage, 0.001f)

        // Verify persistence in preferences
        val prefs = preferencesRepository.userPreferencesFlow.first()
        assertEquals(-140.0, prefs.currentRolloverAmount, 0.001)
        assertEquals("2026-10", prefs.lastProcessedCycleKey)
    }

    // Spec 3.1 & 3.2: Dashboard Recent Activity & Add Expense
    @Test
    fun spec_3_1_and_3_2_addExpense_reducesRemainingBalanceAndPopulatesRecentTransactions() = runTest(testDispatcher) {
        repository.completeOnboarding(monthlyBudget = 500.0)

        for (i in 1..10) {
            repository.addExpense(
                amount = 10.0 * i,
                description = "Expense $i",
                timestamp = epochMillisFor(2026, 9, i)
            )
        }

        val info = repository.budgetInfoFlow.first()
        // Total spent = 10 * (1..10).sum() = 550
        assertEquals(550.0, info.totalSpent, 0.001)
        assertEquals(-50.0, info.remainingBalance, 0.001)
        assertEquals(0.0f, info.remainingPercentage, 0.001f)

        // Recent activity on dashboard initially loads 8 recent transactions, newest first
        val recent = repository.recentTransactionsFlow.first()
        assertEquals(8, recent.size)
        assertEquals("Expense 10", recent[0].description)
        assertEquals("Expense 3", recent[7].description)

        // Lazy loading more transactions fetches the remaining items
        val nextPage = repository.getRecentTransactionsFlow(16).first()
        assertEquals(10, nextPage.size)
        assertEquals("Expense 1", nextPage[9].description)
    }

    // Spec 3.3: Full History Screen - Edit Expense (Amount, Description, Date) & Delete Expense
    @Test
    fun spec_3_3_editAndDeleteExpense_immediatelyRecalculatesCycleAndRolloverBalance() = runTest(testDispatcher) {
        repository.completeOnboarding(monthlyBudget = 500.0)

        repository.addExpense(
            amount = 600.0,
            description = "Original Expense",
            timestamp = epochMillisFor(2026, 9, 15)
        )
        val addedTx = repository.allTransactionsFlow.first().single()

        // 1. Edit amount and description in September ($600 -> $550)
        repository.updateExpense(
            id = addedTx.id,
            amount = 550.0,
            description = "Updated Expense",
            timestamp = addedTx.timestamp
        )

        val updatedSeptInfo = repository.budgetInfoFlow.first()
        assertEquals(-50.0, updatedSeptInfo.remainingBalance, 0.001)

        // 2. Roll over to October 2026: -$50 deficit carried over -> $450 available
        currentDate = LocalDate.of(2026, 10, 5)
        val octInfo = repository.budgetInfoFlow.first()
        assertEquals(-50.0, octInfo.rolloverAmount, 0.001)
        assertEquals(450.0, octInfo.remainingBalance, 0.001)

        // 3. Edit the transaction's date via Date Picker to move it from September to October
        repository.updateExpense(
            id = addedTx.id,
            amount = 550.0,
            description = "Moved to October",
            timestamp = epochMillisFor(2026, 10, 3)
        )

        val afterDateMoveInfo = repository.budgetInfoFlow.first()
        // September now has $0 spent -> +$500 rollover into October -> $1000 October budget - $550 = $450 remaining
        assertEquals(500.0, afterDateMoveInfo.rolloverAmount, 0.001)
        assertEquals(1000.0, afterDateMoveInfo.totalCycleBudget, 0.001)
        assertEquals(550.0, afterDateMoveInfo.totalSpent, 0.001)
        assertEquals(450.0, afterDateMoveInfo.remainingBalance, 0.001)

        // 4. Delete the transaction -> instantly reimburses $550 back into active balance ($1000)
        repository.deleteExpense(addedTx.id)
        val afterDeleteInfo = repository.budgetInfoFlow.first()
        assertEquals(0.0, afterDeleteInfo.totalSpent, 0.001)
        assertEquals(1000.0, afterDeleteInfo.remainingBalance, 0.001)
    }

    // Spec 3.4: Settings Configuration update & batch cycle realignment
    @Test
    fun spec_3_4_updateSettings_updatesConfigurationAndRealignsTransactionCycles() = runTest(testDispatcher) {
        currentDate = LocalDate.of(2026, 9, 10)
        repository.completeOnboarding(monthlyBudget = 500.0)

        // Log an expense on Sep 10 (with refillDay = 1, cycleKey is "2026-09")
        repository.addExpense(
            amount = 100.0,
            description = "Coffee",
            timestamp = epochMillisFor(2026, 9, 10)
        )
        assertEquals("2026-09", repository.allTransactionsFlow.first().single().cycleKey)

        // Change refillDay to 15, monthlyBudget to 800, maxCap to 1600
        // On Sep 10 with refillDay = 15, active cycle started Aug 15 ("2026-08")
        repository.updateSettings(
            monthlyBudget = 800.0,
            refillDay = 15,
            maxCap = 1600.0
        )

        val updatedTx = repository.allTransactionsFlow.first().single()
        assertEquals("2026-08", updatedTx.cycleKey)

        val info = repository.budgetInfoFlow.first()
        assertEquals("2026-08", info.cycleKey)
        assertEquals(800.0, info.monthlyBudget, 0.001)
        assertEquals(15, info.refillDayOfMonth)
        assertEquals(1600.0, info.maxRolloverCap, 0.001)
        assertEquals(700.0, info.remainingBalance, 0.001)
    }

    // Overnight / Resume Date Refresh
    @Test
    fun lifecycle_refreshCurrentDate_updatesDaysRemainingAndMonthRolloverWithoutDbMutation() = runTest(testDispatcher) {
        repository.completeOnboarding(monthlyBudget = 500.0)
        var latestInfo = repository.budgetInfoFlow.first()
        val collector = backgroundScope.launch(testDispatcher) {
            repository.budgetInfoFlow.collect { latestInfo = it }
        }

        assertEquals("2026-09", latestInfo.cycleKey)
        assertEquals(21, latestInfo.daysRemaining)

        // Simulate app resuming on October 3rd without any DB write
        currentDate = LocalDate.of(2026, 10, 3)
        repository.refreshCurrentDate()

        assertEquals("2026-10", latestInfo.cycleKey)
        assertEquals(29, latestInfo.daysRemaining)
        assertEquals(500.0, latestInfo.rolloverAmount, 0.001)
        assertEquals(1000.0, latestInfo.remainingBalance, 0.001)

        collector.cancel()
    }

    // Cent precision & tag deduplication
    @Test
    fun precision_centArithmeticAndDuplicateTagGuard_behavesDeterministically() = runTest(testDispatcher) {
        repository.completeOnboarding(monthlyBudget = 100.0)

        assertTrue(repository.addTag("Coffee", "#26A69A"))
        assertFalse(repository.addTag("coffee", "#FF7043")) // Case-insensitive duplicate rejected
        assertFalse(repository.addTag("Untagged", "#90A4AE")) // Reserved label rejected
        assertEquals(1, repository.tagsFlow.first().size)

        repository.addExpense(amount = 19.99, description = "Book", timestamp = epochMillisFor(2026, 9, 10))
        repository.addExpense(amount = 4.50, description = "Espresso", timestamp = epochMillisFor(2026, 9, 10))
        repository.addExpense(amount = 0.10, description = "Mint", timestamp = epochMillisFor(2026, 9, 10))
        repository.addExpense(amount = 0.20, description = "Gum", timestamp = epochMillisFor(2026, 9, 10))

        val info = repository.budgetInfoFlow.first()
        assertEquals(24.79, info.totalSpent, 0.000001)
        assertEquals(75.21, info.remainingBalance, 0.000001)
    }

    // Backup & Restore round-trip + atomic transaction runner + orderIndex = 0 preservation
    @Test
    fun spec_backupAndRestore_roundTripsPreferencesTagsAndTransactionsAtomically() = runTest(testDispatcher) {
        repository.completeOnboarding(monthlyBudget = 500.0)
        repository.addTag("Travel", "#42A5F5")
        repository.addExpense(
            amount = 620.0,
            description = "Flight",
            timestamp = epochMillisFor(2026, 9, 14),
            tag = "Travel",
            tagColor = "#42A5F5"
        )

        // Advance to October so rollover is -$120
        currentDate = LocalDate.of(2026, 10, 2)
        val preExportInfo = repository.budgetInfoFlow.first()
        assertEquals(-120.0, preExportInfo.rolloverAmount, 0.001)

        val backupJson = repository.exportBackupJson()

        // Wipe state and restore from JSON
        transactionDao.deleteAllTransactions()
        tagDao.deleteAllTags()
        repository.completeOnboarding(monthlyBudget = 100.0)

        val importResult = repository.importBackupJson(backupJson)
        assertTrue(importResult.isSuccess)
        assertEquals(1, atomicTransactionCount)
        assertEquals(1, importResult.getOrThrow().transactionCount)
        assertEquals(1, importResult.getOrThrow().tagCount)
        assertEquals(0, repository.tagsFlow.first().single().orderIndex)

        val restoredInfo = repository.budgetInfoFlow.first()
        assertEquals(500.0, restoredInfo.monthlyBudget, 0.001)
        assertEquals(-120.0, restoredInfo.rolloverAmount, 0.001)
        assertEquals(380.0, restoredInfo.remainingBalance, 0.001)
    }

    @Test
    fun security_importBackupJson_rejectsInvalidPayloadsWithoutDestroyingExistingData() = runTest(testDispatcher) {
        repository.completeOnboarding(monthlyBudget = 500.0)
        repository.addTag("Dining", "#FF7043")
        repository.addExpense(amount = 45.0, description = "Existing Lunch", timestamp = epochMillisFor(2026, 9, 10))

        // 1. Malformed JSON
        assertTrue(repository.importBackupJson("{ not valid json }").isFailure)

        // 2. Unsupported future backup version
        val futureVersionJson = """
            {
              "version": 99,
              "exportedAt": "2026-09-15T00:00:00Z",
              "preferences": {
                "monthlyRefillAmount": 500.0,
                "refillDayOfMonth": 1,
                "maxRolloverCap": 1000.0,
                "totalExtraSaved": 0.0,
                "currentRolloverAmount": 0.0,
                "hasCompletedOnboarding": true,
                "lastProcessedCycleKey": "2026-09"
              },
              "tags": [],
              "transactions": []
            }
        """.trimIndent()
        assertTrue(repository.importBackupJson(futureVersionJson).isFailure)

        // 3. Negative monthly budget
        val negativeBudgetJson = """
            {
              "version": 2,
              "exportedAt": "2026-09-15T00:00:00Z",
              "preferences": {
                "monthlyRefillAmount": -500.0,
                "refillDayOfMonth": 1,
                "maxRolloverCap": 1000.0,
                "totalExtraSaved": 0.0,
                "currentRolloverAmount": 0.0,
                "hasCompletedOnboarding": true,
                "lastProcessedCycleKey": "2026-09"
              },
              "tags": [],
              "transactions": []
            }
        """.trimIndent()
        assertTrue(repository.importBackupJson(negativeBudgetJson).isFailure)

        // 4. Negative transaction amount
        val negativeTxJson = """
            {
              "version": 2,
              "exportedAt": "2026-09-15T00:00:00Z",
              "preferences": {
                "monthlyRefillAmount": 500.0,
                "refillDayOfMonth": 1,
                "maxRolloverCap": 1000.0,
                "totalExtraSaved": 0.0,
                "currentRolloverAmount": 0.0,
                "hasCompletedOnboarding": true,
                "lastProcessedCycleKey": "2026-09"
              },
              "tags": [],
              "transactions": [
                {
                  "amount": -25.0,
                  "description": "Tampered",
                  "timestamp": 1757462400000,
                  "cycleKey": "2026-09"
                }
              ]
            }
        """.trimIndent()
        assertTrue(repository.importBackupJson(negativeTxJson).isFailure)

        // 5. Malformed cycleKey or invalid colorHex
        val invalidCycleKeyJson = """
            {
              "version": 2,
              "exportedAt": "2026-09-15T00:00:00Z",
              "preferences": {
                "monthlyRefillAmount": 500.0,
                "refillDayOfMonth": 1,
                "maxRolloverCap": 1000.0,
                "totalExtraSaved": 0.0,
                "currentRolloverAmount": 0.0,
                "hasCompletedOnboarding": true,
                "lastProcessedCycleKey": "2026-09"
              },
              "tags": [{"name": "Food", "colorHex": "not-hex", "orderIndex": 0}],
              "transactions": []
            }
        """.trimIndent()
        assertTrue(repository.importBackupJson(invalidCycleKeyJson).isFailure)

        // Verify original data was preserved untouched across all failed imports
        assertEquals(0, atomicTransactionCount)
        val existingTxs = repository.allTransactionsFlow.first()
        assertEquals(1, existingTxs.size)
        assertEquals("Existing Lunch", existingTxs.single().description)
        assertEquals(1, repository.tagsFlow.first().size)
    }
}

internal class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

internal class FakeTransactionDao : TransactionDao {
    private var nextId = 1L
    private val transactionsState = MutableStateFlow<List<TransactionEntity>>(emptyList())

    override fun getAllTransactions(): Flow<List<TransactionEntity>> =
        transactionsState.map { list -> list.sortedByDescending { it.timestamp } }

    override fun getRecentTransactions(limit: Int): Flow<List<TransactionEntity>> =
        getAllTransactions().map { it.take(limit) }

    override fun getTransactionsForCycle(cycleKey: String): Flow<List<TransactionEntity>> =
        getAllTransactions().map { list -> list.filter { it.cycleKey == cycleKey } }

    override fun getTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> =
        getAllTransactions().map { list -> list.filter { it.timestamp in startTime until endTime } }

    override suspend fun getTransactionById(id: Long): TransactionEntity? =
        transactionsState.value.find { it.id == id }

    override suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val id = if (transaction.id == 0L) nextId++ else transaction.id
        val entity = transaction.copy(id = id)
        transactionsState.value = transactionsState.value.filterNot { it.id == id } + entity
        return id
    }

    override suspend fun insertAll(transactions: List<TransactionEntity>) {
        transactions.forEach { insertTransaction(it) }
    }

    override suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionsState.value = transactionsState.value.map {
            if (it.id == transaction.id) transaction else it
        }
    }

    override suspend fun updateTransactions(transactions: List<TransactionEntity>) {
        val updatesById = transactions.associateBy { it.id }
        transactionsState.value = transactionsState.value.map { existing ->
            updatesById[existing.id] ?: existing
        }
    }

    override suspend fun deleteTransaction(transaction: TransactionEntity) {
        deleteTransactionById(transaction.id)
    }

    override suspend fun deleteTransactionById(id: Long) {
        transactionsState.value = transactionsState.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllTransactions() {
        transactionsState.value = emptyList()
    }
}

internal class FakeTagDao : TagDao {
    private var nextId = 1L
    private val tagsState = MutableStateFlow<List<TagEntity>>(emptyList())

    override fun getAllTags(): Flow<List<TagEntity>> =
        tagsState.map { list -> list.sortedWith(compareBy<TagEntity> { it.orderIndex }.thenBy { it.id }) }

    override suspend fun getTagByNameIgnoreCase(name: String): TagEntity? =
        tagsState.value.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun insertTag(tag: TagEntity): Long {
        val id = if (tag.id == 0L) nextId++ else tag.id
        val entity = tag.copy(id = id)
        tagsState.value = tagsState.value.filterNot { it.id == id } + entity
        return id
    }

    override suspend fun insertAll(tags: List<TagEntity>) {
        tags.forEach { insertTag(it) }
    }

    override suspend fun deleteTagById(id: Long) {
        tagsState.value = tagsState.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllTags() {
        tagsState.value = emptyList()
    }

    override suspend fun getTagCount(): Int = tagsState.value.size
}
