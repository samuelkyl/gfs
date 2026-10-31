package ca.sleung.gfs.ui.screens.history

import androidx.compose.runtime.Immutable
import ca.sleung.gfs.data.local.db.entity.TagEntity
import ca.sleung.gfs.data.local.db.entity.TransactionEntity
import ca.sleung.gfs.domain.model.CategoryBreakdown

@Immutable
data class HistoryUiState(
    val transactions: List<TransactionEntity> = emptyList(),
    val totalSpent: Double = 0.0,
    val selectedTransaction: TransactionEntity? = null,
    val availableCycles: List<String> = emptyList(),
    val selectedCycle: String? = null, // null = Current Cycle, "ALL" = All Time, or specific past cycle key
    val currentCycleKey: String = "",
    val remainingBalance: Double = 0.0,
    val breakdown: List<CategoryBreakdown> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val isLoading: Boolean = false
)
