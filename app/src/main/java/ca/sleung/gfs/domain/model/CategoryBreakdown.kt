package ca.sleung.gfs.domain.model

/**
 * Represents a single category slice in the spending breakdown.
 */
data class CategoryBreakdown(
    val tag: String,
    val colorHex: String,
    val totalAmount: Double,
    val percentage: Float // 0f..100f
)
