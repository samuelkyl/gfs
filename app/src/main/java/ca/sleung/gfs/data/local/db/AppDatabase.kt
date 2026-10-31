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
 * - v4: Migrated `transactions.amount` from REAL (Double) to INTEGER (cents) to eliminate
 *       IEEE-754 precision issues at the database layer.
 */
@Database(
    entities = [TransactionEntity::class, TagEntity::class],
    version = 4,
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
