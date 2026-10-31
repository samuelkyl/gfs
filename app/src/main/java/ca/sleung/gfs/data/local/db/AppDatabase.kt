package ca.sleung.gfs.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import ca.sleung.gfs.data.local.db.dao.TagDao
import ca.sleung.gfs.data.local.db.dao.TransactionDao
import ca.sleung.gfs.data.local.db.entity.TagEntity
import ca.sleung.gfs.data.local.db.entity.TransactionEntity

/**
 * Primary Room database storing user transactions and custom tags.
 *
 * Schema history:
 * - v1: Initial `transactions` table.
 * - v2: Added `tag` & `tagColor` columns on `transactions` and created `tags` table.
 * - v3: Added query performance indices on `transactions(timestamp)`, `transactions(cycleKey)`,
 *       and `tags(orderIndex)`.
 */
@Database(
    entities = [TransactionEntity::class, TagEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun tagDao(): TagDao
}

/**
 * Abstraction over [androidx.room.withTransaction] allowing multi-DAO atomic writes
 * in production while remaining trivially testable with in-memory DAO fakes.
 */
fun interface DatabaseTransactionRunner {
    suspend operator fun invoke(block: suspend () -> Unit)
}
