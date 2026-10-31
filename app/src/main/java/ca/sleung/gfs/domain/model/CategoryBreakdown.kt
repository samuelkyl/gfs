package ca.sleung.gfs.domain.model

/**
 * Represents a single category slice in the spending breakdown.
 * Amounts are canonically tracked in integer cents.
 */
data class CategoryBreakdown(
    val tag: String,
    val colorHex: String,
    val totalAmountCents: Long,
    val percentage: Float // 0f..100f
) {
    val totalAmount: Double get() = totalAmountCents / 100.0

    constructor(
        tag: String,
        colorHex: String,
        totalAmount: Double,
        percentage: Float
    ) : this(
        tag = tag,
        colorHex = colorHex,
        totalAmountCents = Math.round(totalAmount * 100.0),
        percentage = percentage
    )
}
