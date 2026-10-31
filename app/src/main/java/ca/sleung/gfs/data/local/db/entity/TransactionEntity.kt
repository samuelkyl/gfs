package ca.sleung.gfs.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ca.sleung.gfs.domain.model.Transaction

/**
 * Represents a single logged guilt-free spending expense.
 * Money is stored as integer cents ([amount]) in the database to prevent floating-point drift.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["cycleKey"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "amount")
    val amount: Long,
    val description: String,
    val timestamp: Long,
    val cycleKey: String,
    val tag: String? = null,
    val tagColor: String? = null
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        amountCents = amount,
        description = description,
        timestamp = timestamp,
        cycleKey = cycleKey,
        tag = tag,
        tagColor = tagColor
    )
}

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amountCents,
    description = description,
    timestamp = timestamp,
    cycleKey = cycleKey,
    tag = tag,
    tagColor = tagColor
)
