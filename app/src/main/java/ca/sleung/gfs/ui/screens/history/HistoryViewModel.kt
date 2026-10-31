package ca.sleung.gfs.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.sleung.gfs.data.repository.BudgetRepository
import ca.sleung.gfs.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: BudgetRepository
) : ViewModel() {

    private val _selectedTransaction = MutableStateFlow<Transaction?>(null)
    private val _selectedCycle = MutableStateFlow<String?>(null) // null = current cycle, "ALL" = all time

    private val _historyFlow = _selectedCycle.flatMapLatest { cycle ->
        repository.getCycleHistoryFlow(cycle)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        _historyFlow,
        repository.tagsFlow,
        _selectedTransaction
    ) { history, tags, selectedTx ->
        HistoryUiState(
            transactions = history.transactions,
            totalSpent = history.totalSpent,
            selectedTransaction = selectedTx,
            availableCycles = history.availableCycles,
            selectedCycle = history.selectedCycle,
            currentCycleKey = history.currentCycleKey,
            remainingBalance = history.remainingBalance,
            breakdown = history.breakdown,
            tags = tags,
            isLoading = false,
            hideDecimalPlaces = history.hideDecimalPlaces
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true)
    )

    fun selectTransaction(tx: Transaction) {
        _selectedTransaction.value = tx
    }

    fun dismissEditSheet() {
        _selectedTransaction.value = null
    }

    fun clearSelectedTransaction() {
        dismissEditSheet()
    }

    fun selectCycle(cycleKey: String?) {
        _selectedCycle.value = cycleKey
    }

    fun updateExpense(
        id: Long,
        amount: Double,
        description: String,
        timestamp: Long,
        tag: String? = null,
        tagColor: String? = null
    ) {
        viewModelScope.launch {
            repository.updateExpense(
                id = id,
                amount = amount,
                description = description,
                timestamp = timestamp,
                tag = tag,
                tagColor = tagColor
            )
            _selectedTransaction.value = null
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            repository.deleteExpense(id)
            _selectedTransaction.value = null
        }
    }
}
