package ca.sleung.gfs.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import ca.sleung.gfs.data.local.db.AppDatabase
import ca.sleung.gfs.data.local.db.DatabaseTransactionRunner
import ca.sleung.gfs.data.local.db.dao.TagDao
import ca.sleung.gfs.data.local.db.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tag` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tagColor` TEXT DEFAULT NULL")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tags` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `colorHex` TEXT NOT NULL,
                    `orderIndex` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            seedDefaultTags(db)
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_cycleKey` ON `transactions` (`cycleKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_orderIndex` ON `tags` (`orderIndex`)")
        }
    }

    private fun seedDefaultTags(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO `tags` (`name`, `colorHex`, `orderIndex`) VALUES ('Eating Out', '#FF7043', 0)")
        db.execSQL("INSERT INTO `tags` (`name`, `colorHex`, `orderIndex`) VALUES ('Shopping', '#AB47BC', 1)")
        db.execSQL("INSERT INTO `tags` (`name`, `colorHex`, `orderIndex`) VALUES ('Entertainment', '#42A5F5', 2)")
        db.execSQL("INSERT INTO `tags` (`name`, `colorHex`, `orderIndex`) VALUES ('Coffee & Drinks', '#26A69A', 3)")
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "gfs.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    seedDefaultTags(db)
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(database: AppDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    @Singleton
    fun provideTagDao(database: AppDatabase): TagDao {
        return database.tagDao()
    }

    @Provides
    @Singleton
    fun provideDatabaseTransactionRunner(database: AppDatabase): DatabaseTransactionRunner {
        return DatabaseTransactionRunner { block ->
            database.withTransaction { block() }
        }
    }
}
