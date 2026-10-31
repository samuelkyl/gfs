package ca.sleung.gfs.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.sleung.gfs.data.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: BudgetRepository
) : ViewModel() {

    fun completeOnboarding(monthlyBudget: Double, onFinished: () -> Unit) {
        viewModelScope.launch {
            repository.completeOnboarding(monthlyBudget)
            onFinished()
        }
    }
}
