package ca.sleung.gfs.domain.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class CycleDates(
    val startDate: LocalDate,
    val nextRefillDate: LocalDate,
    val daysRemaining: Int,
    val cycleName: String,
    val cycleKey: String
)

data class RolloverResult(
    val rolloverAmount: Double,
    val overflowAmount: Double,
    val newTotalExtraSaved: Double
)

data class CycleRolloverResolution(
    val rolloverAmount: Double,
    val overflowAmount: Double,
    val newTotalExtraSaved: Double,
    val processedCycleKey: String,
    val firstCycleKey: String,
    val shouldPersist: Boolean
)

/**
 * Pure domain engine responsible for billing cycle boundary math, short-month clamping,
 * surplus rollover capping, extra-saved overflow tracking, and deficit carryover.
 */
@Singleton
class CycleCalculationEngine @Inject constructor() {

    companion object {
        /**
         * Rounds a finite monetary [Double] to 2 decimal places (cents) using half-up rounding
         * to prevent IEEE-754 floating-point drift across repeated additions/subtractions.
         */
        fun roundToCents(value: Double): Double {
            if (!value.isFinite()) return 0.0
            val rounded = BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
            return if (rounded == -0.0) 0.0 else rounded
        }
    }

    /**
     * Calculates the cycle date boundaries for a given [today] date and anchor [refillDayOfMonth].
     *
     * Short-month rule: When a calendar month has fewer days than [refillDayOfMonth]
     * (e.g., Feb 28/29 or 30-day months when anchor is 31), the refill clamps to the
     * last calendar day of that month.
     */
    fun calculateCycleDates(today: LocalDate, refillDayOfMonth: Int): CycleDates {
        val safeRefillDay = refillDayOfMonth.coerceIn(1, 31)
        val currentMonth = YearMonth.from(today)
        val clampedCurrentMonthDay = min(safeRefillDay, currentMonth.lengthOfMonth())
        val thisMonthRefill = currentMonth.atDay(clampedCurrentMonthDay)

        val startDate: LocalDate
        val nextRefillDate: LocalDate

        if (today.isBefore(thisMonthRefill)) {
            // Before this month's refill: active cycle began in the previous month
            val prevMonth = currentMonth.minusMonths(1)
            val clampedPrevDay = min(safeRefillDay, prevMonth.lengthOfMonth())
            startDate = prevMonth.atDay(clampedPrevDay)
            nextRefillDate = thisMonthRefill
        } else {
            // On or after this month's refill: active cycle began this month
            startDate = thisMonthRefill
            val nextMonth = currentMonth.plusMonths(1)
            val clampedNextDay = min(safeRefillDay, nextMonth.lengthOfMonth())
            nextRefillDate = nextMonth.atDay(clampedNextDay)
        }

        val daysRemaining = max(0, ChronoUnit.DAYS.between(today, nextRefillDate).toInt())
        val cycleName = startDate.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val cycleKey = "${startDate.year}-${startDate.monthValue.toString().padStart(2, '0')}"

        return CycleDates(
            startDate = startDate,
            nextRefillDate = nextRefillDate,
            daysRemaining = daysRemaining,
            cycleName = cycleName,
            cycleKey = cycleKey
        )
    }

    /**
     * Calculates rollover, overflow, and accumulated extra saved based on previous balance and max cap.
     * - Surplus above [maxRolloverCap] is tracked in [RolloverResult.newTotalExtraSaved].
     * - Deficit (`< 0`) carries over directly into the next cycle, reducing available budget.
     */
    fun computeRollover(
        previousRemainingBalance: Double,
        maxRolloverCap: Double,
        currentExtraSaved: Double
    ): RolloverResult {
        val cleanBalance = roundToCents(previousRemainingBalance)
        val cleanCap = roundToCents(max(0.0, maxRolloverCap))
        val cleanExtra = roundToCents(currentExtraSaved)

        return when {
            cleanBalance > 0.0 -> {
                val allowableRollover = roundToCents(min(cleanBalance, cleanCap))
                val overflow = roundToCents(cleanBalance - allowableRollover)
                RolloverResult(
                    rolloverAmount = allowableRollover,
                    overflowAmount = overflow,
                    newTotalExtraSaved = roundToCents(cleanExtra + overflow)
                )
            }
            cleanBalance < 0.0 -> {
                RolloverResult(
                    rolloverAmount = cleanBalance,
                    overflowAmount = 0.0,
                    newTotalExtraSaved = cleanExtra
                )
            }
            else -> {
                RolloverResult(
                    rolloverAmount = 0.0,
                    overflowAmount = 0.0,
                    newTotalExtraSaved = cleanExtra
                )
            }
        }
    }

    /**
     * Resolves the rollover and extra saved amounts for [currentCycleKey] by stepping through
     * all completed cycles prior to [currentCycleKey].
     *
     * Handles:
     * - Negative balances (deficits) carrying over into the new cycle and reducing available budget.
     * - Positive balances rolling over up to [maxRolloverCap], with surplus added to extra saved.
     * - Multi-month gaps where one or more cycles elapsed without opening the app.
     * - Retroactive edits/deletions of past-cycle transactions.
     * - Existing installations upgrading where prior cycle transitions were not yet persisted.
     */
    fun resolveCycleRollover(
        currentCycleKey: String,
        lastProcessedCycleKey: String,
        firstCycleKey: String = "",
        currentRolloverAmount: Double,
        currentExtraSaved: Double,
        monthlyRefillAmount: Double,
        maxRolloverCap: Double,
        spentByCycle: Map<String, Double>
    ): CycleRolloverResolution {
        val currentMonth = parseYearMonth(currentCycleKey)
            ?: return CycleRolloverResolution(
                rolloverAmount = roundToCents(currentRolloverAmount),
                overflowAmount = 0.0,
                newTotalExtraSaved = roundToCents(currentExtraSaved),
                processedCycleKey = lastProcessedCycleKey,
                firstCycleKey = firstCycleKey,
                shouldPersist = false
            )

        val lastProcessedMonth = parseYearMonth(lastProcessedCycleKey)
        val recordedFirstMonth = parseYearMonth(firstCycleKey)
        val earliestTxMonth = spentByCycle.keys.mapNotNull { parseYearMonth(it) }.minOrNull()

        val anchorFirstMonth = listOfNotNull(
            recordedFirstMonth ?: earliestTxMonth,
            lastProcessedMonth,
            currentMonth
        ).minOrNull() ?: currentMonth

        val effectiveFirstMonth = listOfNotNull(
            anchorFirstMonth,
            earliestTxMonth
        ).minOrNull() ?: currentMonth

        val resolvedFirstCycleKey = anchorFirstMonth.toString()

        if (!effectiveFirstMonth.isBefore(currentMonth)) {
            val shouldPersist = currentCycleKey != lastProcessedCycleKey ||
                resolvedFirstCycleKey != firstCycleKey
            return CycleRolloverResolution(
                rolloverAmount = roundToCents(currentRolloverAmount),
                overflowAmount = 0.0,
                newTotalExtraSaved = roundToCents(currentExtraSaved),
                processedCycleKey = currentCycleKey,
                firstCycleKey = resolvedFirstCycleKey,
                shouldPersist = shouldPersist
            )
        }

        val baselineRollover = if (
            lastProcessedMonth != null &&
            effectiveFirstMonth == lastProcessedMonth &&
            recordedFirstMonth == lastProcessedMonth &&
            (earliestTxMonth == null || !earliestTxMonth.isBefore(lastProcessedMonth)) &&
            lastProcessedMonth.isBefore(currentMonth)
        ) {
            roundToCents(currentRolloverAmount)
        } else {
            0.0
        }

        val overflowStartMonth = when {
            lastProcessedMonth != null && lastProcessedMonth.isBefore(currentMonth) -> lastProcessedMonth
            firstCycleKey.isBlank() && currentRolloverAmount == 0.0 && currentExtraSaved == 0.0 -> effectiveFirstMonth
            else -> currentMonth
        }

        var rollover = baselineRollover
        var extraSaved = roundToCents(currentExtraSaved)
        var totalOverflow = 0.0
        var month = effectiveFirstMonth

        while (month.isBefore(currentMonth)) {
            val key = month.toString()
            val spentInCycle = roundToCents(spentByCycle[key] ?: 0.0)
            val cycleBudget = roundToCents(monthlyRefillAmount + rollover)
            val remainingBalance = roundToCents(cycleBudget - spentInCycle)

            val countOverflow = !month.isBefore(overflowStartMonth)
            val stepResult = computeRollover(
                previousRemainingBalance = remainingBalance,
                maxRolloverCap = maxRolloverCap,
                currentExtraSaved = extraSaved
            )

            rollover = stepResult.rolloverAmount
            if (countOverflow) {
                totalOverflow = roundToCents(totalOverflow + stepResult.overflowAmount)
                extraSaved = stepResult.newTotalExtraSaved
            }

            month = month.plusMonths(1)
        }

        val shouldPersist = currentCycleKey != lastProcessedCycleKey ||
            resolvedFirstCycleKey != firstCycleKey ||
            abs(rollover - currentRolloverAmount) > 0.0001 ||
            abs(extraSaved - currentExtraSaved) > 0.0001

        return CycleRolloverResolution(
            rolloverAmount = rollover,
            overflowAmount = totalOverflow,
            newTotalExtraSaved = extraSaved,
            processedCycleKey = currentCycleKey,
            firstCycleKey = resolvedFirstCycleKey,
            shouldPersist = shouldPersist
        )
    }

    fun isValidCycleKey(cycleKey: String): Boolean = parseYearMonth(cycleKey) != null

    private fun parseYearMonth(cycleKey: String): YearMonth? {
        if (cycleKey.isBlank()) return null
        return runCatching { YearMonth.parse(cycleKey.trim()) }.getOrNull()
    }
}
