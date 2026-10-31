package ca.sleung.gfs.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.sleung.gfs.data.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: BudgetRepository
) : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 8
    }

    private val _showAddSheet = MutableStateFlow(false)
    private val _visibleLimit = MutableStateFlow(PAGE_SIZE)

    private val _recentTransactionsFlow = _visibleLimit.flatMapLatest { limit ->
        repository.getRecentTransactionsFlow(limit)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.budgetInfoFlow,
        _recentTransactionsFlow,
        repository.tagsFlow,
        _showAddSheet
    ) { budgetInfo, recentTransactions, tags, showAdd ->
        DashboardUiState(
            budgetInfo = budgetInfo,
            recentTransactions = recentTransactions,
            tags = tags,
            isLoading = false,
            showAddExpenseSheet = showAdd
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    fun loadMoreTransactions() {
        val loadedCount = uiState.value.recentTransactions.size
        if (loadedCount >= _visibleLimit.value) {
            _visibleLimit.update { it + PAGE_SIZE }
        }
    }

    fun openAddExpenseSheet() {
        _showAddSheet.value = true
    }

    fun closeAddExpenseSheet() {
        _showAddSheet.value = false
    }

    fun addExpense(
        amount: Double,
        description: String,
        tag: String? = null,
        tagColor: String? = null
    ) {
        viewModelScope.launch {
            repository.addExpense(
                amount = amount,
                description = description,
                tag = tag,
                tagColor = tagColor
            )
            _showAddSheet.value = false
        }
    }
}
