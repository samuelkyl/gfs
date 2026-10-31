package ca.sleung.gfs.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.sleung.gfs.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_addsTagColumnsAndSeedsDefaultTags() {
        var db = helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO `transactions` (`id`, `amount`, `description`, `timestamp`, `cycleKey`)
                VALUES (1, 45.50, 'Groceries', 1760000000000, '2026-10')
                """.trimIndent()
            )
            close()
        }

        db = helper.runMigrationsAndValidate(testDbName, 2, true, DatabaseModule.MIGRATION_1_2)

        val cursor = db.query("SELECT id, amount, description, timestamp, cycleKey, tag, tagColor FROM transactions WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1L, cursor.getLong(0))
        assertEquals(45.50, cursor.getDouble(1), 0.001)
        assertEquals("Groceries", cursor.getString(2))
        assertEquals(1760000000000L, cursor.getLong(3))
        assertEquals("2026-10", cursor.getString(4))
        assertTrue(cursor.isNull(5))
        assertTrue(cursor.isNull(6))
        cursor.close()

        val tagCursor = db.query("SELECT name, colorHex, orderIndex FROM tags ORDER BY orderIndex ASC")
        val seededTags = mutableListOf<Triple<String, String, Int>>()
        while (tagCursor.moveToNext()) {
            seededTags.add(Triple(tagCursor.getString(0), tagCursor.getString(1), tagCursor.getInt(2)))
        }
        tagCursor.close()

        assertEquals(4, seededTags.size)
        assertEquals("Eating Out", seededTags[0].first)
        assertEquals("#FF7043", seededTags[0].second)
        assertEquals("Shopping", seededTags[1].first)
        assertEquals("Entertainment", seededTags[2].first)
        assertEquals("Coffee & Drinks", seededTags[3].first)

        db.close()
    }

    @Test
    fun migrate2To3_createsIndicesAndPreservesData() {
        var db = helper.createDatabase(testDbName, 2).apply {
            execSQL(
                """
                INSERT INTO `transactions` (`id`, `amount`, `description`, `timestamp`, `cycleKey`, `tag`, `tagColor`)
                VALUES (1, 12.00, 'Lunch', 1760100000000, '2026-10', 'Eating Out', '#FF7043')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `tags` (`id`, `name`, `colorHex`, `orderIndex`)
                VALUES (1, 'Custom Tag', '#42A5F5', 0)
                """.trimIndent()
            )
            close()
        }

        db = helper.runMigrationsAndValidate(testDbName, 3, true, DatabaseModule.MIGRATION_2_3)

        val txCursor = db.query("SELECT id, amount, description, tag FROM transactions WHERE id = 1")
        assertTrue(txCursor.moveToFirst())
        assertEquals(12.00, txCursor.getDouble(1), 0.001)
        assertEquals("Eating Out", txCursor.getString(3))
        txCursor.close()

        val indexCursor = db.query("SELECT name FROM sqlite_master WHERE type = 'index'")
        val indices = mutableSetOf<String>()
        while (indexCursor.moveToNext()) {
            indices.add(indexCursor.getString(0))
        }
        indexCursor.close()

        assertTrue(indices.contains("index_transactions_timestamp"))
        assertTrue(indices.contains("index_transactions_cycleKey"))
        assertTrue(indices.contains("index_tags_orderIndex"))

        db.close()
    }

    @Test
    fun migrate3To4_convertsDoubleAmountToIntegerCents() {
        var db = helper.createDatabase(testDbName, 3).apply {
            execSQL(
                """
                INSERT INTO `transactions` (`id`, `amount`, `description`, `timestamp`, `cycleKey`, `tag`, `tagColor`)
                VALUES (1, 45.50, 'Groceries', 1760000000000, '2026-10', 'Eating Out', '#FF7043')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO `transactions` (`id`, `amount`, `description`, `timestamp`, `cycleKey`, `tag`, `tagColor`)
                VALUES (2, 9.99, 'Coffee', 1760001000000, '2026-10', 'Coffee & Drinks', '#26A69A')
                """.trimIndent()
            )
            close()
        }

        db = helper.runMigrationsAndValidate(testDbName, 4, true, DatabaseModule.MIGRATION_3_4)

        val tx1Cursor = db.query("SELECT id, amount, description FROM transactions WHERE id = 1")
        assertTrue(tx1Cursor.moveToFirst())
        assertEquals(4550L, tx1Cursor.getLong(1)) // 45.50 converted to 4550 cents
        assertEquals("Groceries", tx1Cursor.getString(2))
        tx1Cursor.close()

        val tx2Cursor = db.query("SELECT id, amount, description FROM transactions WHERE id = 2")
        assertTrue(tx2Cursor.moveToFirst())
        assertEquals(999L, tx2Cursor.getLong(1)) // 9.99 converted to 999 cents
        assertEquals("Coffee", tx2Cursor.getString(2))
        tx2Cursor.close()

        val indexCursor = db.query("SELECT name FROM sqlite_master WHERE type = 'index'")
        val indices = mutableSetOf<String>()
        while (indexCursor.moveToNext()) {
            indices.add(indexCursor.getString(0))
        }
        indexCursor.close()

        assertTrue(indices.contains("index_transactions_timestamp"))
        assertTrue(indices.contains("index_transactions_cycleKey"))

        db.close()
    }

    @Test
    fun migrateAll_1To4_preservesDataAcrossAllMigrations() {
        var db = helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO `transactions` (`id`, `amount`, `description`, `timestamp`, `cycleKey`)
                VALUES (42, 99.99, 'Initial V1 Data', 1759000000000, '2026-09')
                """.trimIndent()
            )
            close()
        }

        db = helper.runMigrationsAndValidate(
            testDbName,
            4,
            true,
            DatabaseModule.MIGRATION_1_2,
            DatabaseModule.MIGRATION_2_3,
            DatabaseModule.MIGRATION_3_4
        )

        val cursor = db.query("SELECT id, amount, description, cycleKey FROM transactions WHERE id = 42")
        assertTrue(cursor.moveToFirst())
        assertEquals(9999L, cursor.getLong(1)) // 99.99 converted to 9999 cents
        assertEquals("Initial V1 Data", cursor.getString(2))
        assertEquals("2026-09", cursor.getString(3))
        cursor.close()

        db.close()
    }
}
