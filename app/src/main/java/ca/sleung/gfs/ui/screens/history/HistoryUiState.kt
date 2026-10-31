package ca.sleung.gfs.ui.screens.history

import androidx.compose.runtime.Immutable
import ca.sleung.gfs.domain.model.CategoryBreakdown
import ca.sleung.gfs.domain.model.Tag
import ca.sleung.gfs.domain.model.Transaction

@Immutable
data class HistoryUiState(
    val transactions: List<Transaction> = emptyList(),
    val totalSpent: Double = 0.0,
    val selectedTransaction: Transaction? = null,
    val availableCycles: List<String> = emptyList(),
    val selectedCycle: String? = null, // null = Current Cycle, "ALL" = All Time, or specific past cycle key
    val currentCycleKey: String = "",
    val remainingBalance: Double = 0.0,
    val breakdown: List<CategoryBreakdown> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isLoading: Boolean = false,
    val hideDecimalPlaces: Boolean = true
)
