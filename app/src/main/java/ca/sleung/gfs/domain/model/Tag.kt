package ca.sleung.gfs.domain.model

/**
 * Domain representation of an expense category tag.
 */
data class Tag(
    val id: Long = 0,
    val name: String,
    val colorHex: String,
    val orderIndex: Int = 0
)
