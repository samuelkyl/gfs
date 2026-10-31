package ca.sleung.gfs.domain.model

/**
 * Encapsulates the complete budget calculations for the active billing cycle.
 * All monetary amounts are canonically tracked as exact integer cents.
 */
data class BudgetInfo(
    val cycleKey: String = "",
    val cycleName: String = "",
    val daysRemaining: Int = 0,
    val monthlyBudgetCents: Long = 0L,
    val rolloverAmountCents: Long = 0L,
    val totalCycleBudgetCents: Long = 0L,
    val totalSpentCents: Long = 0L,
    val remainingBalanceCents: Long = 0L,
    val remainingPercentage: Float = 0.0f,
    val totalExtraSavedCents: Long = 0L,
    val refillDayOfMonth: Int = 1,
    val maxRolloverCapCents: Long = 0L,
    val hasCompletedOnboarding: Boolean = false,
    val hideDecimalPlaces: Boolean = true
) {
    val monthlyBudget: Double get() = monthlyBudgetCents / 100.0
    val rolloverAmount: Double get() = rolloverAmountCents / 100.0
    val totalCycleBudget: Double get() = totalCycleBudgetCents / 100.0
    val totalSpent: Double get() = totalSpentCents / 100.0
    val remainingBalance: Double get() = remainingBalanceCents / 100.0
    val totalExtraSaved: Double get() = totalExtraSavedCents / 100.0
    val maxRolloverCap: Double get() = maxRolloverCapCents / 100.0

    companion object {
        fun fromDouble(
            cycleKey: String = "",
            cycleName: String = "",
            daysRemaining: Int = 0,
            monthlyBudget: Double = 0.0,
            rolloverAmount: Double = 0.0,
            totalCycleBudget: Double = 0.0,
            totalSpent: Double = 0.0,
            remainingBalance: Double = 0.0,
            remainingPercentage: Float = 0.0f,
            totalExtraSaved: Double = 0.0,
            refillDayOfMonth: Int = 1,
            maxRolloverCap: Double = 0.0,
            hasCompletedOnboarding: Boolean = false,
            hideDecimalPlaces: Boolean = true
        ): BudgetInfo = BudgetInfo(
            cycleKey = cycleKey,
            cycleName = cycleName,
            daysRemaining = daysRemaining,
            monthlyBudgetCents = Math.round(monthlyBudget * 100.0),
            rolloverAmountCents = Math.round(rolloverAmount * 100.0),
            totalCycleBudgetCents = Math.round(totalCycleBudget * 100.0),
            totalSpentCents = Math.round(totalSpent * 100.0),
            remainingBalanceCents = Math.round(remainingBalance * 100.0),
            remainingPercentage = remainingPercentage,
            totalExtraSavedCents = Math.round(totalExtraSaved * 100.0),
            refillDayOfMonth = refillDayOfMonth,
            maxRolloverCapCents = Math.round(maxRolloverCap * 100.0),
            hasCompletedOnboarding = hasCompletedOnboarding,
            hideDecimalPlaces = hideDecimalPlaces
        )
    }
}
