package ca.sleung.gfs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.sleung.gfs.data.local.preferences.BudgetPreferencesRepository
import ca.sleung.gfs.data.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: BudgetPreferencesRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    val hasCompletedOnboarding: StateFlow<Boolean?> = preferencesRepository.userPreferencesFlow
        .map { it.hasCompletedOnboarding }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun onAppResumed() {
        budgetRepository.refreshCurrentDate()
    }
}
