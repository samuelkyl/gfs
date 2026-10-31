package ca.sleung.gfs.data.repository

import ca.sleung.gfs.data.local.db.DatabaseTransactionRunner
import ca.sleung.gfs.data.local.db.dao.TagDao
import ca.sleung.gfs.data.local.db.dao.TransactionDao
import ca.sleung.gfs.data.local.db.entity.TagEntity
import ca.sleung.gfs.data.local.db.entity.TransactionEntity
import ca.sleung.gfs.data.local.preferences.BudgetPreferencesRepository
import ca.sleung.gfs.di.DefaultDispatcher
import ca.sleung.gfs.di.IoDispatcher
import ca.sleung.gfs.domain.engine.CycleCalculationEngine
import ca.sleung.gfs.domain.engine.CycleCalculationEngine.Companion.roundToCents
import ca.sleung.gfs.domain.model.BackupPreferences
import ca.sleung.gfs.domain.model.BackupTag
import ca.sleung.gfs.domain.model.BackupTransaction
import ca.sleung.gfs.domain.model.BudgetInfo
import ca.sleung.gfs.domain.model.CategoryBreakdown
import ca.sleung.gfs.domain.model.CycleHistory
import ca.sleung.gfs.domain.model.GfsBackupData
import ca.sleung.gfs.domain.model.ImportResult
import ca.sleung.gfs.domain.model.Tag
import ca.sleung.gfs.domain.model.Transaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Single source of truth coordinating Room DB, DataStore preferences, and cycle calculations.
 * Exposes clean domain models ([Transaction], [Tag], [BudgetInfo], [CycleHistory]) to the UI layer,
 * keeping the presentation layer completely decoupled from Room entities.
 */
@Singleton
class BudgetRepository internal constructor(
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
    private val preferencesRepository: BudgetPreferencesRepository,
    private val cycleEngine: CycleCalculationEngine,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
    private val transactionRunner: DatabaseTransactionRunner = DatabaseTransactionRunner { it() },
    private val currentDateProvider: () -> LocalDate = { LocalDate.now() }
) {

    @Inject
    constructor(
        transactionDao: TransactionDao,
        tagDao: TagDao,
        preferencesRepository: BudgetPreferencesRepository,
        cycleEngine: CycleCalculationEngine,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        transactionRunner: DatabaseTransactionRunner
    ) : this(
        transactionDao = transactionDao,
        tagDao = tagDao,
        preferencesRepository = preferencesRepository,
        cycleEngine = cycleEngine,
        ioDispatcher = ioDispatcher,
        defaultDispatcher = defaultDispatcher,
        transactionRunner = transactionRunner,
        currentDateProvider = { LocalDate.now() }
    )

    companion object {
        const val CURRENT_BACKUP_VERSION = 2
        const val MAX_MONETARY_AMOUNT = 10_000_000.0
        const val MAX_TAG_COUNT = 200
        const val MAX_TRANSACTION_COUNT = 50_000
        const val MAX_TAG_NAME_LENGTH = 40
        const val MAX_DESCRIPTION_LENGTH = 200
        const val DEFAULT_TAG_COLOR = "#FF7043"
        const val UNTAGGED_COLOR = "#90A4AE"
        const val UNTAGGED_LABEL = "Untagged"
        const val DEFAULT_EXPENSE_DESCRIPTION = "Guilt-Free Expense"

        private val HEX_COLOR_REGEX = Regex("^#?(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
        private val exportJsonFormat = Json { prettyPrint = true }
        private val importJsonFormat = Json { ignoreUnknownKeys = true }
    }

    private val appScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val dateRefreshTrigger = MutableStateFlow(0L)
    private val rolloverPersistMutex = Mutex()

    /**
     * Triggers re-evaluation of date-dependent streams (e.g., when the app resumes on a new day).
     */
    fun refreshCurrentDate() {
        dateRefreshTrigger.update { it + 1L }
        appScope.launch {
            reconcileRollover()
        }
    }

    val recentTransactionsFlow: Flow<List<Transaction>> =
        transactionDao.getRecentTransactions(8).map { list -> list.map { it.toDomain() } }

    fun getRecentTransactionsFlow(limit: Int): Flow<List<Transaction>> =
        transactionDao.getRecentTransactions(limit).map { list -> list.map { it.toDomain() } }

    val allTransactionsFlow: Flow<List<Transaction>> =
        transactionDao.getAllTransactions().map { list -> list.map { it.toDomain() } }

    val tagsFlow: Flow<List<Tag>> =
        tagDao.getAllTags().map { list -> list.map { it.toDomain() } }

    /**
     * Pure, side-effect-free read stream projecting active cycle budget info.
     * Guaranteed never to execute persistence writes inside stream transformation.
     */
    val budgetInfoFlow: Flow<BudgetInfo> = combine(
        preferencesRepository.userPreferencesFlow,
        transactionDao.getAllTransactions(),
        dateRefreshTrigger
    ) { prefs, allTransactions, _ ->
        val today = currentDateProvider()
        val cycleDates = cycleEngine.calculateCycleDates(today, prefs.refillDayOfMonth)

        val spentByCycleCents = allTransactions
            .groupBy { it.cycleKey }
            .mapValues { (_, txList) -> txList.sumOf { it.amount } }

        val resolution = cycleEngine.resolveCycleRolloverCents(
            currentCycleKey = cycleDates.cycleKey,
            lastProcessedCycleKey = prefs.lastProcessedCycleKey,
            firstCycleKey = prefs.firstCycleKey,
            currentRolloverCents = prefs.currentRolloverCents,
            currentExtraSavedCents = prefs.totalExtraSavedCents,
            monthlyRefillCents = prefs.monthlyRefillCents,
            maxRolloverCapCents = prefs.maxRolloverCapCents,
            spentByCycleCents = spentByCycleCents
        )

        val totalSpentCents = spentByCycleCents[cycleDates.cycleKey] ?: 0L
        val effectiveRolloverCents = resolution.rolloverAmountCents
        val effectiveExtraSavedCents = resolution.newTotalExtraSavedCents

        val totalCycleBudgetCents = prefs.monthlyRefillCents + effectiveRolloverCents
        val remainingBalanceCents = totalCycleBudgetCents - totalSpentCents

        val remainingPercentage = if (totalCycleBudgetCents > 0L) {
            (remainingBalanceCents.toDouble() / totalCycleBudgetCents.toDouble()).coerceIn(0.0, 1.0).toFloat()
        } else {
            0.0f
        }

        BudgetInfo(
            cycleKey = cycleDates.cycleKey,
            cycleName = cycleDates.cycleName,
            daysRemaining = cycleDates.daysRemaining,
            monthlyBudgetCents = prefs.monthlyRefillCents,
            rolloverAmountCents = effectiveRolloverCents,
            totalCycleBudgetCents = totalCycleBudgetCents,
            totalSpentCents = totalSpentCents,
            remainingBalanceCents = remainingBalanceCents,
            remainingPercentage = remainingPercentage,
            totalExtraSavedCents = effectiveExtraSavedCents,
            refillDayOfMonth = prefs.refillDayOfMonth,
            maxRolloverCapCents = prefs.maxRolloverCapCents,
            hasCompletedOnboarding = prefs.hasCompletedOnboarding,
            hideDecimalPlaces = prefs.hideDecimalPlaces
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    /**
     * Explicit operation reconciling and persisting rollover state to DataStore.
     */
    suspend fun reconcileRollover() = withContext(ioDispatcher) {
        rolloverPersistMutex.withLock {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            if (!prefs.hasCompletedOnboarding) return@withLock
            val allTransactions = transactionDao.getAllTransactions().first()
            val today = currentDateProvider()
            val cycleDates = cycleEngine.calculateCycleDates(today, prefs.refillDayOfMonth)

            val spentByCycleCents = allTransactions
                .groupBy { it.cycleKey }
                .mapValues { (_, txList) -> txList.sumOf { it.amount } }

            val resolution = cycleEngine.resolveCycleRolloverCents(
                currentCycleKey = cycleDates.cycleKey,
                lastProcessedCycleKey = prefs.lastProcessedCycleKey,
                firstCycleKey = prefs.firstCycleKey,
                currentRolloverCents = prefs.currentRolloverCents,
                currentExtraSavedCents = prefs.totalExtraSavedCents,
                monthlyRefillCents = prefs.monthlyRefillCents,
                maxRolloverCapCents = prefs.maxRolloverCapCents,
                spentByCycleCents = spentByCycleCents
            )

            if (resolution.shouldPersist) {
                preferencesRepository.updateRolloverAndOverflowCents(
                    rolloverCents = resolution.rolloverAmountCents,
                    extraSavedCents = resolution.newTotalExtraSavedCents,
                    cycleKey = resolution.processedCycleKey,
                    firstCycleKey = resolution.firstCycleKey
                )
            }
        }
    }

    /**
     * Centralized history stream computing transactions, totals, and breakdown once.
     * Prevents screens from re-aggregating and re-filtering history separately.
     */
    fun getCycleHistoryFlow(selectedCycleKey: String?): Flow<CycleHistory> = combine(
        transactionDao.getAllTransactions(),
        preferencesRepository.userPreferencesFlow,
        tagDao.getAllTags(),
        dateRefreshTrigger
    ) { allTxEntities, prefs, allTagEntities, _ ->
        val today = currentDateProvider()
        val currentCycleKey = cycleEngine.calculateCycleDates(today, prefs.refillDayOfMonth).cycleKey
        val allTx = allTxEntities.map { it.toDomain() }
        val tags = allTagEntities.map { it.toDomain() }

        val effectiveSelectedCycle = if (selectedCycleKey == currentCycleKey) null else selectedCycleKey

        val filteredTransactions = when (effectiveSelectedCycle) {
            "ALL" -> allTx
            null -> allTx.filter { it.cycleKey == currentCycleKey }
            else -> allTx.filter { it.cycleKey == effectiveSelectedCycle }
        }

        val historicalCycles = allTx
            .map { it.cycleKey }
            .distinct()
            .filter { it.isNotBlank() && it != currentCycleKey }
            .sortedDescending()

        val totalSpentCents = filteredTransactions.sumOf { it.amountCents }

        val spentByCycleCents = allTxEntities
            .groupBy { it.cycleKey }
            .mapValues { (_, txList) -> txList.sumOf { it.amount } }

        val resolution = cycleEngine.resolveCycleRolloverCents(
            currentCycleKey = currentCycleKey,
            lastProcessedCycleKey = prefs.lastProcessedCycleKey,
            firstCycleKey = prefs.firstCycleKey,
            currentRolloverCents = prefs.currentRolloverCents,
            currentExtraSavedCents = prefs.totalExtraSavedCents,
            monthlyRefillCents = prefs.monthlyRefillCents,
            maxRolloverCapCents = prefs.maxRolloverCapCents,
            spentByCycleCents = spentByCycleCents
        )
        val activeSpentCents = spentByCycleCents[currentCycleKey] ?: 0L
        val activeRemainingCents = (prefs.monthlyRefillCents + resolution.rolloverAmountCents) - activeSpentCents

        val breakdown = if (totalSpentCents <= 0L) {
            emptyList()
        } else {
            val tagsMap = tags.associate { it.name to it.colorHex }
            filteredTransactions
                .groupBy { it.tag?.takeIf { t -> t.isNotBlank() } ?: UNTAGGED_LABEL }
                .map { (tagName, txList) ->
                    val groupTotalCents = txList.sumOf { it.amountCents }
                    val color = if (tagName == UNTAGGED_LABEL) {
                        UNTAGGED_COLOR
                    } else {
                        txList.firstOrNull { !it.tagColor.isNullOrBlank() }?.tagColor
                            ?: tagsMap[tagName]
                            ?: DEFAULT_TAG_COLOR
                    }
                    val pct = ((groupTotalCents.toDouble() / totalSpentCents.toDouble()) * 100.0).toFloat()
                    CategoryBreakdown(
                        tag = tagName,
                        colorHex = color,
                        totalAmountCents = groupTotalCents,
                        percentage = pct
                    )
                }
                .sortedByDescending { it.totalAmountCents }
        }

        CycleHistory(
            transactions = filteredTransactions,
            totalSpentCents = totalSpentCents,
            remainingBalanceCents = activeRemainingCents,
            breakdown = breakdown,
            availableCycles = historicalCycles,
            selectedCycle = effectiveSelectedCycle,
            currentCycleKey = currentCycleKey,
            hideDecimalPlaces = prefs.hideDecimalPlaces
        )
    }
        .distinctUntilChanged()
        .flowOn(defaultDispatcher)

    fun getBreakdownForCycleFlow(cycleKey: String?): Flow<List<CategoryBreakdown>> =
        getCycleHistoryFlow(cycleKey).map { it.breakdown }

    suspend fun addExpense(
        amount: Double,
        description: String,
        timestamp: Long = System.currentTimeMillis(),
        tag: String? = null,
        tagColor: String? = null
    ) = withContext(ioDispatcher) {
        val cleanAmount = roundToCents(amount)
        if (cleanAmount <= 0.0 || cleanAmount > MAX_MONETARY_AMOUNT) return@withContext

        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val cycleDates = cycleEngine.calculateCycleDates(date, prefs.refillDayOfMonth)
        val cleanTag = tag?.trim()?.takeIf { it.isNotBlank() }?.take(MAX_TAG_NAME_LENGTH)
        val cleanDesc = description.trim()
            .take(MAX_DESCRIPTION_LENGTH)
            .ifBlank { cleanTag ?: DEFAULT_EXPENSE_DESCRIPTION }

        val entity = TransactionEntity(
            amount = Math.round(cleanAmount * 100.0),
            description = cleanDesc,
            timestamp = timestamp,
            cycleKey = cycleDates.cycleKey,
            tag = cleanTag,
            tagColor = tagColor?.trim()?.takeIf { it.isNotBlank() }
        )
        transactionDao.insertTransaction(entity)
        reconcileRollover()
    }

    suspend fun updateExpense(
        id: Long,
        amount: Double,
        description: String,
        timestamp: Long,
        tag: String? = null,
        tagColor: String? = null
    ) = withContext(ioDispatcher) {
        val cleanAmount = roundToCents(amount)
        if (cleanAmount <= 0.0 || cleanAmount > MAX_MONETARY_AMOUNT) return@withContext

        val existing = transactionDao.getTransactionById(id) ?: return@withContext
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val cycleDates = cycleEngine.calculateCycleDates(date, prefs.refillDayOfMonth)
        val cleanTag = tag?.trim()?.takeIf { it.isNotBlank() }?.take(MAX_TAG_NAME_LENGTH)
        val cleanDesc = description.trim()
            .take(MAX_DESCRIPTION_LENGTH)
            .ifBlank { cleanTag ?: DEFAULT_EXPENSE_DESCRIPTION }

        val updated = existing.copy(
            amount = Math.round(cleanAmount * 100.0),
            description = cleanDesc,
            timestamp = timestamp,
            cycleKey = cycleDates.cycleKey,
            tag = cleanTag,
            tagColor = tagColor?.trim()?.takeIf { it.isNotBlank() }
        )
        transactionDao.updateTransaction(updated)
        reconcileRollover()
    }

    suspend fun deleteExpense(id: Long) = withContext(ioDispatcher) {
        transactionDao.deleteTransactionById(id)
        reconcileRollover()
    }

    suspend fun addTag(name: String, colorHex: String): Boolean = withContext(ioDispatcher) {
        val trimmedName = name.trim().take(MAX_TAG_NAME_LENGTH)
        val trimmedColor = colorHex.trim()
        if (trimmedName.isBlank() || trimmedName.equals(UNTAGGED_LABEL, ignoreCase = true)) {
            return@withContext false
        }
        if (tagDao.getTagByNameIgnoreCase(trimmedName) != null) {
            return@withContext false
        }
        val safeColor = if (HEX_COLOR_REGEX.matches(trimmedColor)) trimmedColor else DEFAULT_TAG_COLOR
        val count = tagDao.getTagCount()
        tagDao.insertTag(
            TagEntity(
                name = trimmedName,
                colorHex = safeColor,
                orderIndex = count
            )
        )
        true
    }

    suspend fun deleteTag(id: Long) = withContext(ioDispatcher) {
        tagDao.deleteTagById(id)
    }

    suspend fun resetExtraSaved() = withContext(ioDispatcher) {
        rolloverPersistMutex.withLock {
            val today = currentDateProvider()
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val cycleKey = cycleEngine.calculateCycleDates(today, prefs.refillDayOfMonth).cycleKey
            val allTransactions = transactionDao.getAllTransactions().first()
            val spentByCycleCents = allTransactions
                .groupBy { it.cycleKey }
                .mapValues { (_, txList) -> txList.sumOf { it.amount } }
            val resolution = cycleEngine.resolveCycleRolloverCents(
                currentCycleKey = cycleKey,
                lastProcessedCycleKey = prefs.lastProcessedCycleKey,
                firstCycleKey = prefs.firstCycleKey,
                currentRolloverCents = prefs.currentRolloverCents,
                currentExtraSavedCents = 0L,
                monthlyRefillCents = prefs.monthlyRefillCents,
                maxRolloverCapCents = prefs.maxRolloverCapCents,
                spentByCycleCents = spentByCycleCents
            )
            preferencesRepository.updateRolloverAndOverflowCents(
                rolloverCents = resolution.rolloverAmountCents,
                extraSavedCents = 0L,
                cycleKey = cycleKey,
                firstCycleKey = resolution.firstCycleKey
            )
        }
    }

    suspend fun exportBackupJson(): String = withContext(ioDispatcher) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val allTags = tagDao.getAllTags().first()
        val allTx = transactionDao.getAllTransactions().first()

        val backup = GfsBackupData(
            version = CURRENT_BACKUP_VERSION,
            exportedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            preferences = BackupPreferences(
                monthlyRefillAmount = prefs.monthlyRefillAmount,
                refillDayOfMonth = prefs.refillDayOfMonth,
                maxRolloverCap = prefs.maxRolloverCap,
                totalExtraSaved = prefs.totalExtraSaved,
                currentRolloverAmount = prefs.currentRolloverAmount,
                hasCompletedOnboarding = prefs.hasCompletedOnboarding,
                lastProcessedCycleKey = prefs.lastProcessedCycleKey,
                firstCycleKey = prefs.firstCycleKey,
                hideDecimalPlaces = prefs.hideDecimalPlaces
            ),
            tags = allTags.map { BackupTag(it.name, it.colorHex, it.orderIndex) },
            transactions = allTx.map {
                BackupTransaction(
                    amount = it.amount / 100.0,
                    description = it.description,
                    timestamp = it.timestamp,
                    cycleKey = it.cycleKey,
                    tag = it.tag,
                    tagColor = it.tagColor
                )
            }
        )

        exportJsonFormat.encodeToString(GfsBackupData.serializer(), backup)
    }

    suspend fun importBackupJson(jsonString: String): Result<ImportResult> = withContext(ioDispatcher) {
        runCatching {
            val backup = importJsonFormat.decodeFromString(GfsBackupData.serializer(), jsonString)

            require(backup.version in 1..CURRENT_BACKUP_VERSION) {
                "Unsupported backup schema version: ${backup.version}"
            }

            // Validate backup data integrity and bounds before mutating local state
            val prefs = backup.preferences
            require(
                prefs.monthlyRefillAmount.isFinite() &&
                    prefs.monthlyRefillAmount > 0.0 &&
                    prefs.monthlyRefillAmount <= MAX_MONETARY_AMOUNT
            ) {
                "Invalid monthly refill amount in backup"
            }
            require(prefs.refillDayOfMonth in 1..31) {
                "Invalid refill day of month in backup"
            }
            require(
                prefs.maxRolloverCap.isFinite() &&
                    prefs.maxRolloverCap >= 0.0 &&
                    prefs.maxRolloverCap <= MAX_MONETARY_AMOUNT * 10
            ) {
                "Invalid max rollover cap in backup"
            }
            require(
                prefs.currentRolloverAmount.isFinite() &&
                    abs(prefs.currentRolloverAmount) <= MAX_MONETARY_AMOUNT * 10
            ) {
                "Invalid rollover amount in backup"
            }
            require(
                prefs.totalExtraSaved.isFinite() &&
                    prefs.totalExtraSaved >= 0.0 &&
                    prefs.totalExtraSaved <= MAX_MONETARY_AMOUNT * 100
            ) {
                "Invalid extra saved amount in backup"
            }
            require(prefs.lastProcessedCycleKey.isBlank() || cycleEngine.isValidCycleKey(prefs.lastProcessedCycleKey)) {
                "Invalid lastProcessedCycleKey in backup"
            }
            require(prefs.firstCycleKey.isBlank() || cycleEngine.isValidCycleKey(prefs.firstCycleKey)) {
                "Invalid firstCycleKey in backup"
            }
            require(backup.tags.size <= MAX_TAG_COUNT) {
                "Backup exceeds maximum supported tag count"
            }
            require(backup.transactions.size <= MAX_TRANSACTION_COUNT) {
                "Backup exceeds maximum supported transaction count"
            }

            backup.tags.forEach { tag ->
                val cleanName = tag.name.trim()
                val cleanColor = tag.colorHex.trim()
                require(cleanName.isNotEmpty() && cleanName.length <= MAX_TAG_NAME_LENGTH) {
                    "Invalid tag name in backup"
                }
                require(HEX_COLOR_REGEX.matches(cleanColor)) {
                    "Invalid tag colorHex in backup"
                }
            }

            backup.transactions.forEach { tx ->
                require(tx.amount.isFinite() && tx.amount > 0.0 && tx.amount <= MAX_MONETARY_AMOUNT) {
                    "Invalid transaction amount in backup"
                }
                require(tx.timestamp > 0L) {
                    "Invalid transaction timestamp in backup"
                }
                require(cycleEngine.isValidCycleKey(tx.cycleKey)) {
                    "Invalid transaction cycleKey in backup"
                }
                require(tx.description.trim().length <= MAX_DESCRIPTION_LENGTH) {
                    "Transaction description exceeds maximum length"
                }
                val cleanTagColor = tx.tagColor?.trim()
                require(cleanTagColor.isNullOrEmpty() || HEX_COLOR_REGEX.matches(cleanTagColor)) {
                    "Invalid transaction tagColor in backup"
                }
            }

            val seenTagNames = mutableSetOf<String>()
            val tagEntities = backup.tags
                .filter { seenTagNames.add(it.name.trim().lowercase()) }
                .mapIndexed { index, t ->
                    val normalizedHex = t.colorHex.trim().let { if (it.startsWith("#")) it else "#$it" }
                    TagEntity(
                        name = t.name.trim(),
                        colorHex = normalizedHex,
                        orderIndex = t.orderIndex.takeIf { it >= 0 } ?: index
                    )
                }

            val transactionEntities = backup.transactions.map { tx ->
                val cleanTag = tx.tag?.trim()?.takeIf { it.isNotBlank() }?.take(MAX_TAG_NAME_LENGTH)
                val cleanTagColor = tx.tagColor?.trim()?.takeIf { it.isNotBlank() }?.let {
                    if (it.startsWith("#")) it else "#$it"
                }
                TransactionEntity(
                    amount = Math.round(roundToCents(tx.amount) * 100.0),
                    description = tx.description.trim().ifBlank { cleanTag ?: DEFAULT_EXPENSE_DESCRIPTION },
                    timestamp = tx.timestamp,
                    cycleKey = tx.cycleKey.trim(),
                    tag = cleanTag,
                    tagColor = cleanTagColor
                )
            }

            // Snapshot previous data to roll back Room if preferences restoration fails
            val prevTags = tagDao.getAllTags().first()
            val prevTransactions = transactionDao.getAllTransactions().first()

            // Atomically replace tags and transactions within a single SQLite transaction
            transactionRunner {
                tagDao.replaceAllTags(tagEntities)
                transactionDao.replaceAllTransactions(transactionEntities)
            }

            // Restore preferences, rolling back Room entities if DataStore fails
            try {
                preferencesRepository.restoreAllPreferences(
                    monthlyBudget = prefs.monthlyRefillAmount,
                    refillDay = prefs.refillDayOfMonth,
                    maxCap = prefs.maxRolloverCap,
                    rollover = prefs.currentRolloverAmount,
                    extraSaved = prefs.totalExtraSaved,
                    onboardingCompleted = prefs.hasCompletedOnboarding,
                    lastCycleKey = prefs.lastProcessedCycleKey.trim(),
                    firstCycleKey = prefs.firstCycleKey.trim(),
                    hideDecimalPlaces = prefs.hideDecimalPlaces
                )
            } catch (t: Throwable) {
                transactionRunner {
                    tagDao.replaceAllTags(prevTags)
                    transactionDao.replaceAllTransactions(prevTransactions)
                }
                throw t
            }

            ImportResult(
                transactionCount = transactionEntities.size,
                tagCount = tagEntities.size
            )
        }
    }

    suspend fun completeOnboarding(monthlyBudget: Double) = withContext(ioDispatcher) {
        val cleanBudget = roundToCents(monthlyBudget)
        if (cleanBudget <= 0.0 || cleanBudget > MAX_MONETARY_AMOUNT) return@withContext

        val today = currentDateProvider()
        val cycleDates = cycleEngine.calculateCycleDates(today, 1)
        preferencesRepository.completeOnboarding(cleanBudget)
        preferencesRepository.updateRolloverAndOverflow(
            rollover = 0.0,
            extraSaved = 0.0,
            cycleKey = cycleDates.cycleKey,
            firstCycleKey = cycleDates.cycleKey
        )
    }

    suspend fun updateHideDecimalPlaces(hide: Boolean) = withContext(ioDispatcher) {
        preferencesRepository.updateHideDecimalPlaces(hide)
    }

    suspend fun updateSettings(
        monthlyBudget: Double,
        refillDay: Int,
        maxCap: Double
    ) = withContext(ioDispatcher) {
        val cleanBudget = roundToCents(monthlyBudget)
        val cleanCap = roundToCents(maxCap)
        if (cleanBudget <= 0.0 || cleanBudget > MAX_MONETARY_AMOUNT) return@withContext
        if (cleanCap < 0.0 || cleanCap > MAX_MONETARY_AMOUNT * 10) return@withContext

        val currentPrefs = preferencesRepository.userPreferencesFlow.first()
        val clampedRefillDay = refillDay.coerceIn(1, 31)

        if (currentPrefs.refillDayOfMonth != clampedRefillDay) {
            val allTx = transactionDao.getAllTransactions().first()
            val realigned = allTx.mapNotNull { tx ->
                val txDate = Instant.ofEpochMilli(tx.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                val newCycleKey = cycleEngine.calculateCycleDates(txDate, clampedRefillDay).cycleKey
                if (tx.cycleKey != newCycleKey) tx.copy(cycleKey = newCycleKey) else null
            }
            if (realigned.isNotEmpty()) {
                transactionRunner {
                    transactionDao.updateTransactions(realigned)
                }
            }
        }

        preferencesRepository.updateAllSettings(
            monthlyBudget = cleanBudget,
            refillDay = clampedRefillDay,
            maxCap = cleanCap
        )
        reconcileRollover()
    }
}
