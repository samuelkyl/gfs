package ca.sleung.gfs.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.sleung.gfs.data.local.db.entity.TransactionEntity
import ca.sleung.gfs.data.repository.BudgetRepository
import ca.sleung.gfs.domain.engine.CycleCalculationEngine.Companion.roundToCents
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

    private data class CycleFilteredSnapshot(
        val filteredTransactions: List<TransactionEntity>,
        val historicalCycles: List<String>,
        val selectedCycle: String?,
        val currentCycleKey: String,
        val remainingBalance: Double
    )

    private val _selectedTransaction = MutableStateFlow<TransactionEntity?>(null)
    private val _selectedCycle = MutableStateFlow<String?>(null) // null = current cycle, "ALL" = all time

    private val _cycleFilteredData = combine(
        repository.allTransactionsFlow,
        repository.budgetInfoFlow,
        _selectedCycle
    ) { allTx, budgetInfo, selectedCycle ->
        val currentCycleKey = budgetInfo.cycleKey

        // Normalize selection if user picked the active cycle key directly
        val effectiveSelectedCycle = if (selectedCycle == currentCycleKey) null else selectedCycle

        val filteredTransactions = when (effectiveSelectedCycle) {
            "ALL" -> allTx
            null -> allTx.filter { it.cycleKey == currentCycleKey }
            else -> allTx.filter { it.cycleKey == effectiveSelectedCycle }
        }

        // Only include non-current cycles in historical pills (sorted newest-first)
        // since "Current Cycle" is already rendered as the primary first chip.
        val historicalCycles = allTx
            .map { it.cycleKey }
            .distinct()
            .filter { it.isNotBlank() && it != currentCycleKey }
            .sortedDescending()

        CycleFilteredSnapshot(
            filteredTransactions = filteredTransactions,
            historicalCycles = historicalCycles,
            selectedCycle = effectiveSelectedCycle,
            currentCycleKey = currentCycleKey,
            remainingBalance = budgetInfo.remainingBalance
        )
    }

    private val _breakdownFlow = _selectedCycle.flatMapLatest { cycle ->
        repository.getBreakdownForCycleFlow(cycle)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        _cycleFilteredData,
        _breakdownFlow,
        repository.tagsFlow,
        _selectedTransaction
    ) { snapshot, breakdown, tags, selectedTx ->
        HistoryUiState(
            transactions = snapshot.filteredTransactions,
            totalSpent = roundToCents(snapshot.filteredTransactions.sumOf { it.amount }),
            selectedTransaction = selectedTx,
            availableCycles = snapshot.historicalCycles,
            selectedCycle = snapshot.selectedCycle,
            currentCycleKey = snapshot.currentCycleKey,
            remainingBalance = snapshot.remainingBalance,
            breakdown = breakdown,
            tags = tags,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState(isLoading = true)
    )

    fun selectCycle(cycleKey: String?) {
        _selectedCycle.value = cycleKey
    }

    fun selectTransaction(tx: TransactionEntity) {
        _selectedTransaction.value = tx
    }

    fun clearSelectedTransaction() {
        _selectedTransaction.value = null
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
