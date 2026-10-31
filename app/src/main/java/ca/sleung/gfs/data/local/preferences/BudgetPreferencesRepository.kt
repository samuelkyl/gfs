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

data class UserPreferences(
    val monthlyRefillAmount: Double,
    val refillDayOfMonth: Int,
    val maxRolloverCap: Double,
    val totalExtraSaved: Double,
    val currentRolloverAmount: Double,
    val hasCompletedOnboarding: Boolean,
    val lastProcessedCycleKey: String,
    val firstCycleKey: String = ""
)

@Singleton
class BudgetPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.dataStore)

    companion object {
        val KEY_MONTHLY_REFILL = doublePreferencesKey("monthly_refill_amount")
        val KEY_REFILL_DAY = intPreferencesKey("refill_day_of_month")
        val KEY_MAX_ROLLOVER_CAP = doublePreferencesKey("max_rollover_cap")
        val KEY_TOTAL_EXTRA_SAVED = doublePreferencesKey("total_extra_saved")
        val KEY_CURRENT_ROLLOVER = doublePreferencesKey("current_rollover_amount")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("has_completed_onboarding")
        val KEY_LAST_PROCESSED_CYCLE = stringPreferencesKey("last_processed_cycle_key")
        val KEY_FIRST_CYCLE = stringPreferencesKey("first_cycle_key")
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
        val monthly = preferences[KEY_MONTHLY_REFILL] ?: 500.0
        val day = preferences[KEY_REFILL_DAY] ?: 1
        val cap = preferences[KEY_MAX_ROLLOVER_CAP] ?: (monthly * 2.0)
        val extra = preferences[KEY_TOTAL_EXTRA_SAVED] ?: 0.0
        val rollover = preferences[KEY_CURRENT_ROLLOVER] ?: 0.0
        val onboarding = preferences[KEY_ONBOARDING_COMPLETED] ?: false
        val lastCycle = preferences[KEY_LAST_PROCESSED_CYCLE] ?: ""
        val firstCycle = preferences[KEY_FIRST_CYCLE] ?: ""

        UserPreferences(
            monthlyRefillAmount = monthly,
            refillDayOfMonth = day,
            maxRolloverCap = cap,
            totalExtraSaved = extra,
            currentRolloverAmount = rollover,
            hasCompletedOnboarding = onboarding,
            lastProcessedCycleKey = lastCycle,
            firstCycleKey = firstCycle
        )
    }

    suspend fun updateMonthlyBudget(amount: Double) {
        dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_REFILL] = amount
        }
    }

    suspend fun updateRefillDay(day: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_REFILL_DAY] = day.coerceIn(1, 31)
        }
    }

    suspend fun updateMaxRolloverCap(cap: Double) {
        dataStore.edit { preferences ->
            preferences[KEY_MAX_ROLLOVER_CAP] = cap
        }
    }

    suspend fun updateRolloverAndOverflow(
        rollover: Double,
        extraSaved: Double,
        cycleKey: String,
        firstCycleKey: String? = null
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_CURRENT_ROLLOVER] = rollover
            preferences[KEY_TOTAL_EXTRA_SAVED] = extraSaved
            preferences[KEY_LAST_PROCESSED_CYCLE] = cycleKey
            if (!firstCycleKey.isNullOrBlank()) {
                preferences[KEY_FIRST_CYCLE] = firstCycleKey
            }
        }
    }

    suspend fun completeOnboarding(monthlyBudget: Double) {
        dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_REFILL] = monthlyBudget
            preferences[KEY_REFILL_DAY] = 1
            preferences[KEY_MAX_ROLLOVER_CAP] = monthlyBudget * 2.0
            preferences[KEY_CURRENT_ROLLOVER] = 0.0
            preferences[KEY_TOTAL_EXTRA_SAVED] = 0.0
            preferences[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun updateAllSettings(monthlyBudget: Double, refillDay: Int, maxCap: Double) {
        dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_REFILL] = monthlyBudget
            preferences[KEY_REFILL_DAY] = refillDay.coerceIn(1, 31)
            preferences[KEY_MAX_ROLLOVER_CAP] = maxCap
        }
    }

    suspend fun resetExtraSaved() {
        dataStore.edit { preferences ->
            preferences[KEY_TOTAL_EXTRA_SAVED] = 0.0
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
        firstCycleKey: String = ""
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_MONTHLY_REFILL] = monthlyBudget
            preferences[KEY_REFILL_DAY] = refillDay.coerceIn(1, 31)
            preferences[KEY_MAX_ROLLOVER_CAP] = maxCap
            preferences[KEY_CURRENT_ROLLOVER] = rollover
            preferences[KEY_TOTAL_EXTRA_SAVED] = extraSaved
            preferences[KEY_ONBOARDING_COMPLETED] = onboardingCompleted
            preferences[KEY_LAST_PROCESSED_CYCLE] = lastCycleKey
            preferences[KEY_FIRST_CYCLE] = firstCycleKey.ifBlank { lastCycleKey }
        }
    }
}
