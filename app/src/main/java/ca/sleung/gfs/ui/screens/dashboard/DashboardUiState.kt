package ca.sleung.gfs.ui.screens.dashboard

import androidx.compose.runtime.Immutable
import ca.sleung.gfs.data.local.db.entity.TagEntity
import ca.sleung.gfs.data.local.db.entity.TransactionEntity
import ca.sleung.gfs.domain.model.BudgetInfo

@Immutable
data class DashboardUiState(
    val budgetInfo: BudgetInfo = BudgetInfo(),
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val isLoading: Boolean = false,
    val showAddExpenseSheet: Boolean = false
)
