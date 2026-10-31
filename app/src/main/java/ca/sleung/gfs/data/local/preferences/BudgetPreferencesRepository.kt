package ca.sleung.gfs.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gfs_budget_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * User budget preferences data with monetary amounts canonically stored as integer cents.
 */
data class UserPreferences(
    val monthlyRefillCents: Long,
    val refillDayOfMonth: Int,
    val maxRolloverCapCents: Long,
    val totalExtraSavedCents: Long,
    val currentRolloverCents: Long,
    val hasCompletedOnboarding: Boolean,
    val lastProcessedCycleKey: String,
    val firstCycleKey: String = "",
    val hideDecimalPlaces: Boolean = true
) {
    val monthlyRefillAmount: Double get() = monthlyRefillCents / 100.0
    val maxRolloverCap: Double get() = maxRolloverCapCents / 100.0
    val totalExtraSaved: Double get() = totalExtraSavedCents / 100.0
    val currentRolloverAmount: Double get() = currentRolloverCents / 100.0
}

@Singleton
class BudgetPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.dataStore)

    companion object {
        // Cent-based storage keys (Long)
        val KEY_MONTHLY_REFILL_CENTS = longPreferencesKey("monthly_refill_cents")
        val KEY_MAX_ROLLOVER_CAP_CENTS = longPreferencesKey("max_rollover_cap_cents")
        val KEY_TOTAL_EXTRA_SAVED_CENTS = longPreferencesKey("total_extra_saved_cents")
        val KEY_CURRENT_ROLLOVER_CENTS = longPreferencesKey("current_rollover_cents")

        // Legacy double keys for backward compatibility
        val KEY_LEGACY_MONTHLY_REFILL = doublePreferencesKey("monthly_refill_amount")
        val KEY_LEGACY_MAX_ROLLOVER_CAP = doublePreferencesKey("max_rollover_cap")
        val KEY_LEGACY_TOTAL_EXTRA_SAVED = doublePreferencesKey("total_extra_saved")
        val KEY_LEGACY_CURRENT_ROLLOVER = doublePreferencesKey("current_rollover_amount")

        val KEY_REFILL_DAY = intPreferencesKey("refill_day_of_month")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("has_completed_onboarding")
        val KEY_LAST_PROCESSED_CYCLE = stringPreferencesKey("last_processed_cycle_key")
        val KEY_FIRST_CYCLE = stringPreferencesKey("first_cycle_key")
        val KEY_HIDE_DECIMAL_PLACES = booleanPreferencesKey("hide_decimal_places")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val monthlyCents = preferences[KEY_MONTHLY_REFILL_CENTS]
                ?: preferences[KEY_LEGACY_MONTHLY_REFILL]?.let { Math.round(it * 100.0) }
                ?: 50000L // default $500.00
            val day = preferences[KEY_REFILL_DAY] ?: 1
            val capCents = preferences[KEY_MAX_ROLLOVER_CAP_CENTS]
                ?: preferences[KEY_LEGACY_MAX_ROLLOVER_CAP]?.let { Math.round(it * 100.0) }
                ?: (monthlyCents * 2L)
            val extraCents = preferences[KEY_TOTAL_EXTRA_SAVED_CENTS]
                ?: preferences[KEY_LEGACY_TOTAL_EXTRA_SAVED]?.let { Math.round(it * 100.0) }
                ?: 0L
            val rolloverCents = preferences[KEY_CURRENT_ROLLOVER_CENTS]
                ?: preferences[KEY_LEGACY_CURRENT_ROLLOVER]?.let { Math.round(it * 100.0) }
                ?: 0L
            val onboarding = preferences[KEY_ONBOARDING_COMPLETED] ?: false
            val lastCycle = preferences[KEY_LAST_PROCESSED_CYCLE] ?: ""
            val firstCycle = preferences[KEY_FIRST_CYCLE] ?: ""
            val hideDecimals = preferences[KEY_HIDE_DECIMAL_PLACES] ?: true

            UserPreferences(
                monthlyRefillCents = monthlyCents,
                refillDayOfMonth = day,
                maxRolloverCapCents = capCents,
                totalExtraSavedCents = extraCents,
                currentRolloverCents = rolloverCents,
                hasCompletedOnboarding = onboarding,
                lastProcessedCycleKey = lastCycle,
                firstCycleKey = firstCycle,
                hideDecimalPlaces = hideDecimals
            )
        }

    suspend fun updateMonthlyBudgetCents(cents: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_REFILL_CENTS] = cents
            preferences[KEY_LEGACY_MONTHLY_REFILL] = cents / 100.0
        }
    }

    suspend fun updateMonthlyBudget(amount: Double) {
        updateMonthlyBudgetCents(Math.round(amount * 100.0))
    }

    suspend fun updateRefillDay(day: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_REFILL_DAY] = day.coerceIn(1, 31)
        }
    }

    suspend fun updateMaxRolloverCapCents(capCents: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_MAX_ROLLOVER_CAP_CENTS] = capCents
            preferences[KEY_LEGACY_MAX_ROLLOVER_CAP] = capCents / 100.0
        }
    }

    suspend fun updateMaxRolloverCap(cap: Double) {
        updateMaxRolloverCapCents(Math.round(cap * 100.0))
    }

    suspend fun updateRolloverAndOverflowCents(
        rolloverCents: Long,
        extraSavedCents: Long,
        cycleKey: String,
        firstCycleKey: String? = null
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_CURRENT_ROLLOVER_CENTS] = rolloverCents
            preferences[KEY_LEGACY_CURRENT_ROLLOVER] = rolloverCents / 100.0
            preferences[KEY_TOTAL_EXTRA_SAVED_CENTS] = extraSavedCents
            preferences[KEY_LEGACY_TOTAL_EXTRA_SAVED] = extraSavedCents / 100.0
            preferences[KEY_LAST_PROCESSED_CYCLE] = cycleKey
            if (!firstCycleKey.isNullOrBlank()) {
                preferences[KEY_FIRST_CYCLE] = firstCycleKey
            }
        }
    }

    suspend fun updateRolloverAndOverflow(
        rollover: Double,
        extraSaved: Double,
        cycleKey: String,
        firstCycleKey: String? = null
    ) {
        updateRolloverAndOverflowCents(
            rolloverCents = Math.round(rollover * 100.0),
            extraSavedCents = Math.round(extraSaved * 100.0),
            cycleKey = cycleKey,
            firstCycleKey = firstCycleKey
        )
    }

    suspend fun completeOnboardingCents(monthlyBudgetCents: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_REFILL_CENTS] = monthlyBudgetCents
            preferences[KEY_LEGACY_MONTHLY_REFILL] = monthlyBudgetCents / 100.0
            preferences[KEY_REFILL_DAY] = 1
            preferences[KEY_MAX_ROLLOVER_CAP_CENTS] = monthlyBudgetCents * 2L
            preferences[KEY_LEGACY_MAX_ROLLOVER_CAP] = (monthlyBudgetCents * 2L) / 100.0
            preferences[KEY_CURRENT_ROLLOVER_CENTS] = 0L
            preferences[KEY_LEGACY_CURRENT_ROLLOVER] = 0.0
            preferences[KEY_TOTAL_EXTRA_SAVED_CENTS] = 0L
            preferences[KEY_LEGACY_TOTAL_EXTRA_SAVED] = 0.0
            preferences[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun completeOnboarding(monthlyBudget: Double) {
        completeOnboardingCents(Math.round(monthlyBudget * 100.0))
    }

    suspend fun updateAllSettingsCents(monthlyBudgetCents: Long, refillDay: Int, maxCapCents: Long) {
        dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_REFILL_CENTS] = monthlyBudgetCents
            preferences[KEY_LEGACY_MONTHLY_REFILL] = monthlyBudgetCents / 100.0
            preferences[KEY_REFILL_DAY] = refillDay.coerceIn(1, 31)
            preferences[KEY_MAX_ROLLOVER_CAP_CENTS] = maxCapCents
            preferences[KEY_LEGACY_MAX_ROLLOVER_CAP] = maxCapCents / 100.0
        }
    }

    suspend fun updateAllSettings(monthlyBudget: Double, refillDay: Int, maxCap: Double) {
        updateAllSettingsCents(
            monthlyBudgetCents = Math.round(monthlyBudget * 100.0),
            refillDay = refillDay,
            maxCapCents = Math.round(maxCap * 100.0)
        )
    }

    suspend fun updateHideDecimalPlaces(hide: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HIDE_DECIMAL_PLACES] = hide
        }
    }

    suspend fun resetExtraSaved() {
        dataStore.edit { preferences ->
            preferences[KEY_TOTAL_EXTRA_SAVED_CENTS] = 0L
            preferences[KEY_LEGACY_TOTAL_EXTRA_SAVED] = 0.0
        }
    }

    suspend fun restoreAllPreferencesCents(
        monthlyBudgetCents: Long,
        refillDay: Int,
        maxCapCents: Long,
        rolloverCents: Long,
        extraSavedCents: Long,
        onboardingCompleted: Boolean,
        lastCycleKey: String,
        firstCycleKey: String = "",
        hideDecimalPlaces: Boolean = true
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_REFILL_CENTS] = monthlyBudgetCents
            preferences[KEY_LEGACY_MONTHLY_REFILL] = monthlyBudgetCents / 100.0
            preferences[KEY_REFILL_DAY] = refillDay.coerceIn(1, 31)
            preferences[KEY_MAX_ROLLOVER_CAP_CENTS] = maxCapCents
            preferences[KEY_LEGACY_MAX_ROLLOVER_CAP] = maxCapCents / 100.0
            preferences[KEY_CURRENT_ROLLOVER_CENTS] = rolloverCents
            preferences[KEY_LEGACY_CURRENT_ROLLOVER] = rolloverCents / 100.0
            preferences[KEY_TOTAL_EXTRA_SAVED_CENTS] = extraSavedCents
            preferences[KEY_LEGACY_TOTAL_EXTRA_SAVED] = extraSavedCents / 100.0
            preferences[KEY_ONBOARDING_COMPLETED] = onboardingCompleted
            preferences[KEY_LAST_PROCESSED_CYCLE] = lastCycleKey
            preferences[KEY_FIRST_CYCLE] = firstCycleKey.ifBlank { lastCycleKey }
            preferences[KEY_HIDE_DECIMAL_PLACES] = hideDecimalPlaces
        }
    }

    suspend fun restoreAllPreferences(
        monthlyBudget: Double,
        refillDay: Int,
        maxCap: Double,
        rollover: Double,
        extraSaved: Double,
        onboardingCompleted: Boolean,
        lastCycleKey: String,
        firstCycleKey: String = "",
        hideDecimalPlaces: Boolean = true
    ) {
        restoreAllPreferencesCents(
            monthlyBudgetCents = Math.round(monthlyBudget * 100.0),
            refillDay = refillDay,
            maxCapCents = Math.round(maxCap * 100.0),
            rolloverCents = Math.round(rollover * 100.0),
            extraSavedCents = Math.round(extraSaved * 100.0),
            onboardingCompleted = onboardingCompleted,
            lastCycleKey = lastCycleKey,
            firstCycleKey = firstCycleKey,
            hideDecimalPlaces = hideDecimalPlaces
        )
    }
}
