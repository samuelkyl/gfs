package ca.sleung.gfs.domain.model

/**
 * Encapsulates the complete budget calculations for the active billing cycle.
 */
data class BudgetInfo(
    val cycleKey: String = "",
    val cycleName: String = "",
    val daysRemaining: Int = 0,
    val monthlyBudget: Double = 500.0,
    val rolloverAmount: Double = 0.0,
    val totalCycleBudget: Double = 500.0,
    val totalSpent: Double = 0.0,
    val remainingBalance: Double = 500.0,
    val remainingPercentage: Float = 1.0f,
    val totalExtraSaved: Double = 0.0,
    val refillDayOfMonth: Int = 1,
    val maxRolloverCap: Double = 1000.0,
    val hasCompletedOnboarding: Boolean = false
)
