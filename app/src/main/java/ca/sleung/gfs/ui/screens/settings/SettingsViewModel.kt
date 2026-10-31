package ca.sleung.gfs.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.sleung.gfs.data.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _restoreGeneration = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = combine(
        budgetRepository.budgetInfoFlow,
        budgetRepository.tagsFlow,
        budgetRepository.allTransactionsFlow,
        _statusMessage,
        _restoreGeneration
    ) { budgetInfo, tags, allTx, status, restoreGen ->
        SettingsUiState(
            monthlyBudget = budgetInfo.monthlyBudget,
            refillDayOfMonth = budgetInfo.refillDayOfMonth,
            maxRolloverCap = budgetInfo.maxRolloverCap,
            currentRollover = budgetInfo.rolloverAmount,
            totalExtraSaved = budgetInfo.totalExtraSaved,
            tags = tags,
            transactionCount = allTx.size,
            statusMessage = status,
            restoreGeneration = restoreGen,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoading = true)
    )

    fun saveSettings(monthlyBudget: Double, refillDay: Int, maxCap: Double) {
        if (!monthlyBudget.isFinite() || monthlyBudget <= 0.0 || refillDay !in 1..31 || !maxCap.isFinite() || maxCap < 0.0) {
            _statusMessage.value = "Please enter valid budget settings"
            return
        }
        viewModelScope.launch {
            budgetRepository.updateSettings(
                monthlyBudget = monthlyBudget,
                refillDay = refillDay,
                maxCap = maxCap
            )
            _statusMessage.value = "Settings saved successfully"
        }
    }

    fun resetExtraSaved() {
        viewModelScope.launch {
            budgetRepository.resetExtraSaved()
            _statusMessage.value = "Extra Saved counter reset to $0"
        }
    }

    fun addTag(name: String, colorHex: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val added = budgetRepository.addTag(cleanName, colorHex)
            if (added) {
                _statusMessage.value = "Added tag \"$cleanName\""
            } else {
                _statusMessage.value = "Tag \"$cleanName\" already exists"
            }
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch {
            budgetRepository.deleteTag(id)
            _statusMessage.value = "Tag removed"
        }
    }

    suspend fun getExportJson(): String {
        return budgetRepository.exportBackupJson()
    }

    fun importBackup(jsonString: String) {
        viewModelScope.launch {
            val result = budgetRepository.importBackupJson(jsonString)
            if (result.isSuccess) {
                val data = result.getOrThrow()
                _restoreGeneration.update { it + 1 }
                _statusMessage.value = "Restored ${data.transactionCount} transactions & ${data.tagCount} tags!"
            } else {
                _statusMessage.value = "Failed to import backup: invalid file format"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
