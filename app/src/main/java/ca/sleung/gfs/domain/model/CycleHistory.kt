package ca.sleung.gfs.domain.model

/**
 * Consolidated domain model representing transactions and spending breakdown for a cycle.
 * Centralizing this eliminates duplicated computations across screens.
 */
data class CycleHistory(
    val transactions: List<Transaction> = emptyList(),
    val totalSpentCents: Long = 0L,
    val remainingBalanceCents: Long = 0L,
    val breakdown: List<CategoryBreakdown> = emptyList(),
    val availableCycles: List<String> = emptyList(),
    val selectedCycle: String? = null,
    val currentCycleKey: String = "",
    val hideDecimalPlaces: Boolean = true
) {
    val totalSpent: Double get() = totalSpentCents / 100.0
    val remainingBalance: Double get() = remainingBalanceCents / 100.0
}
