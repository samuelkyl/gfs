package ca.sleung.gfs.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ca.sleung.gfs.domain.model.Tag

/**
 * Represents a customizable expense tag with a distinct display color.
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["orderIndex"])]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorHex: String,
    val orderIndex: Int = 0
) {
    fun toDomain(): Tag = Tag(
        id = id,
        name = name,
        colorHex = colorHex,
        orderIndex = orderIndex
    )
}

fun Tag.toEntity(): TagEntity = TagEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    orderIndex = orderIndex
)
