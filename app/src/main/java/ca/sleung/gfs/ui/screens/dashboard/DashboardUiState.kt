package ca.sleung.gfs.ui.screens.dashboard

import androidx.compose.runtime.Immutable
import ca.sleung.gfs.domain.model.BudgetInfo
import ca.sleung.gfs.domain.model.Tag
import ca.sleung.gfs.domain.model.Transaction

@Immutable
data class DashboardUiState(
    val budgetInfo: BudgetInfo = BudgetInfo(),
    val recentTransactions: List<Transaction> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isLoading: Boolean = false,
    val showAddExpenseSheet: Boolean = false
)
