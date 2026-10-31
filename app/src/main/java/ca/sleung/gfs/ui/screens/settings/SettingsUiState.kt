package ca.sleung.gfs.ui.screens.settings

import androidx.compose.runtime.Immutable
import ca.sleung.gfs.domain.model.Tag

@Immutable
data class SettingsUiState(
    val monthlyBudget: Double = 500.0,
    val refillDayOfMonth: Int = 1,
    val maxRolloverCap: Double = 1000.0,
    val currentRollover: Double = 0.0,
    val totalExtraSaved: Double = 0.0,
    val tags: List<Tag> = emptyList(),
    val transactionCount: Int = 0,
    val statusMessage: String? = null,
    val restoreGeneration: Int = 0,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val hideDecimalPlaces: Boolean = true,
    val hasPendingImport: Boolean = false
)
