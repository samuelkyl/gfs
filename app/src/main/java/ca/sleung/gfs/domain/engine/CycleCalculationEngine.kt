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
    val rolloverAmountCents: Long,
    val overflowAmountCents: Long,
    val newTotalExtraSavedCents: Long
) {
    val rolloverAmount: Double get() = rolloverAmountCents / 100.0
    val overflowAmount: Double get() = overflowAmountCents / 100.0
    val newTotalExtraSaved: Double get() = newTotalExtraSavedCents / 100.0
}

data class CycleRolloverResolution(
    val rolloverAmountCents: Long,
    val overflowAmountCents: Long,
    val newTotalExtraSavedCents: Long,
    val processedCycleKey: String,
    val firstCycleKey: String,
    val shouldPersist: Boolean
) {
    val rolloverAmount: Double get() = rolloverAmountCents / 100.0
    val overflowAmount: Double get() = overflowAmountCents / 100.0
    val newTotalExtraSaved: Double get() = newTotalExtraSavedCents / 100.0
}

/**
 * Pure domain engine responsible for billing cycle boundary math, short-month clamping,
 * surplus rollover capping, extra-saved overflow tracking, and deficit carryover.
 * All monetary calculations operate strictly on integer cents (Long) to eliminate floating-point drift.
 */
@Singleton
class CycleCalculationEngine @Inject constructor() {

    companion object {
        /**
         * Rounds a finite monetary [Double] to 2 decimal places (cents) using half-up rounding.
         */
        fun roundToCents(value: Double): Double {
            if (!value.isFinite()) return 0.0
            val rounded = BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
            return if (rounded == -0.0) 0.0 else rounded
        }

        fun toCents(amount: Double): Long {
            if (!amount.isFinite()) return 0L
            return Math.round(amount * 100.0)
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
     * Calculates rollover, overflow, and accumulated extra saved based on previous balance and max cap in cents.
     * - Surplus above [maxRolloverCapCents] is tracked in [RolloverResult.newTotalExtraSavedCents].
     * - Deficit (`< 0`) carries over directly into the next cycle, reducing available budget.
     */
    fun computeRolloverCents(
        previousRemainingBalanceCents: Long,
        maxRolloverCapCents: Long,
        currentExtraSavedCents: Long
    ): RolloverResult {
        val cleanCap = max(0L, maxRolloverCapCents)

        return when {
            previousRemainingBalanceCents > 0L -> {
                val allowableRollover = min(previousRemainingBalanceCents, cleanCap)
                val overflow = previousRemainingBalanceCents - allowableRollover
                RolloverResult(
                    rolloverAmountCents = allowableRollover,
                    overflowAmountCents = overflow,
                    newTotalExtraSavedCents = currentExtraSavedCents + overflow
                )
            }
            previousRemainingBalanceCents < 0L -> {
                RolloverResult(
                    rolloverAmountCents = previousRemainingBalanceCents,
                    overflowAmountCents = 0L,
                    newTotalExtraSavedCents = currentExtraSavedCents
                )
            }
            else -> {
                RolloverResult(
                    rolloverAmountCents = 0L,
                    overflowAmountCents = 0L,
                    newTotalExtraSavedCents = currentExtraSavedCents
                )
            }
        }
    }

    fun computeRollover(
        previousRemainingBalance: Double,
        maxRolloverCap: Double,
        currentExtraSaved: Double
    ): RolloverResult = computeRolloverCents(
        previousRemainingBalanceCents = toCents(previousRemainingBalance),
        maxRolloverCapCents = toCents(maxRolloverCap),
        currentExtraSavedCents = toCents(currentExtraSaved)
    )

    /**
     * Resolves the rollover and extra saved amounts for [currentCycleKey] by stepping through
     * all completed cycles prior to [currentCycleKey] using integer cent calculations.
     */
    fun resolveCycleRolloverCents(
        currentCycleKey: String,
        lastProcessedCycleKey: String,
        firstCycleKey: String = "",
        currentRolloverCents: Long,
        currentExtraSavedCents: Long,
        monthlyRefillCents: Long,
        maxRolloverCapCents: Long,
        spentByCycleCents: Map<String, Long>
    ): CycleRolloverResolution {
        val currentMonth = parseYearMonth(currentCycleKey)
            ?: return CycleRolloverResolution(
                rolloverAmountCents = currentRolloverCents,
                overflowAmountCents = 0L,
                newTotalExtraSavedCents = currentExtraSavedCents,
                processedCycleKey = lastProcessedCycleKey,
                firstCycleKey = firstCycleKey,
                shouldPersist = false
            )

        val lastProcessedMonth = parseYearMonth(lastProcessedCycleKey)
        val recordedFirstMonth = parseYearMonth(firstCycleKey)
        val earliestTxMonth = spentByCycleCents.keys.mapNotNull { parseYearMonth(it) }.minOrNull()

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
                rolloverAmountCents = currentRolloverCents,
                overflowAmountCents = 0L,
                newTotalExtraSavedCents = currentExtraSavedCents,
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
            currentRolloverCents
        } else {
            0L
        }

        val overflowStartMonth = when {
            lastProcessedMonth != null && lastProcessedMonth.isBefore(currentMonth) -> lastProcessedMonth
            firstCycleKey.isBlank() && currentRolloverCents == 0L && currentExtraSavedCents == 0L -> effectiveFirstMonth
            else -> currentMonth
        }

        var rollover = baselineRollover
        var extraSaved = currentExtraSavedCents
        var totalOverflow = 0L
        var month = effectiveFirstMonth

        while (month.isBefore(currentMonth)) {
            val key = month.toString()
            val spentInCycle = spentByCycleCents[key] ?: 0L
            val cycleBudget = monthlyRefillCents + rollover
            val remainingBalance = cycleBudget - spentInCycle

            val countOverflow = !month.isBefore(overflowStartMonth)
            val stepResult = computeRolloverCents(
                previousRemainingBalanceCents = remainingBalance,
                maxRolloverCapCents = maxRolloverCapCents,
                currentExtraSavedCents = extraSaved
            )

            rollover = stepResult.rolloverAmountCents
            if (countOverflow) {
                totalOverflow += stepResult.overflowAmountCents
                extraSaved = stepResult.newTotalExtraSavedCents
            }

            month = month.plusMonths(1)
        }

        val shouldPersist = currentCycleKey != lastProcessedCycleKey ||
            resolvedFirstCycleKey != firstCycleKey ||
            rollover != currentRolloverCents ||
            extraSaved != currentExtraSavedCents

        return CycleRolloverResolution(
            rolloverAmountCents = rollover,
            overflowAmountCents = totalOverflow,
            newTotalExtraSavedCents = extraSaved,
            processedCycleKey = currentCycleKey,
            firstCycleKey = resolvedFirstCycleKey,
            shouldPersist = shouldPersist
        )
    }

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
        val spentByCycleCents = spentByCycle.mapValues { toCents(it.value) }
        return resolveCycleRolloverCents(
            currentCycleKey = currentCycleKey,
            lastProcessedCycleKey = lastProcessedCycleKey,
            firstCycleKey = firstCycleKey,
            currentRolloverCents = toCents(currentRolloverAmount),
            currentExtraSavedCents = toCents(currentExtraSaved),
            monthlyRefillCents = toCents(monthlyRefillAmount),
            maxRolloverCapCents = toCents(maxRolloverCap),
            spentByCycleCents = spentByCycleCents
        )
    }

    fun isValidCycleKey(cycleKey: String): Boolean = parseYearMonth(cycleKey) != null

    private fun parseYearMonth(cycleKey: String): YearMonth? {
        if (cycleKey.isBlank()) return null
        return runCatching { YearMonth.parse(cycleKey.trim()) }.getOrNull()
    }
}
