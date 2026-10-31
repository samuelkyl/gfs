package ca.sleung.gfs.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GfsBackupData(
    val version: Int = 2,
    val exportedAt: String,
    val preferences: BackupPreferences,
    val tags: List<BackupTag>,
    val transactions: List<BackupTransaction>
)

@Serializable
data class BackupPreferences(
    val monthlyRefillAmount: Double,
    val refillDayOfMonth: Int,
    val maxRolloverCap: Double,
    val totalExtraSaved: Double,
    val currentRolloverAmount: Double,
    val hasCompletedOnboarding: Boolean,
    val lastProcessedCycleKey: String,
    val firstCycleKey: String = ""
)

@Serializable
data class BackupTag(
    val name: String,
    val colorHex: String,
    val orderIndex: Int = 0
)

@Serializable
data class BackupTransaction(
    val amount: Double,
    val description: String,
    val timestamp: Long,
    val cycleKey: String,
    val tag: String? = null,
    val tagColor: String? = null
)

data class ImportResult(
    val transactionCount: Int,
    val tagCount: Int
)
