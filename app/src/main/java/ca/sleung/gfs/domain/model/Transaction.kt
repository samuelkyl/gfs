package ca.sleung.gfs.domain.model

/**
 * Domain representation of a financial expense transaction.
 *
 * Monetary values are strictly stored as integer cents ([amountCents]) to prevent
 * IEEE-754 floating-point inaccuracies across the application.
 */
data class Transaction(
    val id: Long = 0,
    val amountCents: Long,
    val description: String,
    val timestamp: Long,
    val cycleKey: String,
    val tag: String? = null,
    val tagColor: String? = null
) {
    /**
     * Convenience property returning amount in whole currency units (dollars).
     */
    val amount: Double
        get() = amountCents / 100.0

    companion object {
        fun fromDouble(
            id: Long = 0,
            amount: Double,
            description: String,
            timestamp: Long,
            cycleKey: String,
            tag: String? = null,
            tagColor: String? = null
        ): Transaction = Transaction(
            id = id,
            amountCents = Math.round(amount * 100.0),
            description = description,
            timestamp = timestamp,
            cycleKey = cycleKey,
            tag = tag,
            tagColor = tagColor
        )
    }
}
