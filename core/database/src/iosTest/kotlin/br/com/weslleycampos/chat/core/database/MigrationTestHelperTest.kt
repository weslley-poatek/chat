@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrationTestHelperTest {
    private val path = "${NSTemporaryDirectory()}chat-migration-${Random.nextLong()}.db"
    private val helper = MigrationTestHelper(
        schemaDirectoryPath = schemaDirectory,
        fileName = path,
        driver = BundledSQLiteDriver(),
        databaseClass = ChatDatabase::class,
        databaseFactory = ChatDatabaseConstructor::initialize,
    )

    @AfterTest
    fun deleteDatabase() {
        helper.finished()
        listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, error = null) }
    }

    @Test
    fun createsVersionOneFromTheExportedSchema() = runTest {
        val connection = helper.createDatabase(version = 1)

        val hasPlaceholder = connection
            .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'placeholder'")
            .use { it.step() }

        assertTrue(hasPlaceholder)
    }

    @Test
    fun migratesVersionOneToTwoAddingConversationsAndMessages() = runTest {
        helper.createDatabase(version = 1).close()

        val connection = helper.runMigrationsAndValidate(version = 2)

        val tables = connection
            .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('conversations', 'messages')")
            .use { statement -> buildSet { while (statement.step()) add(statement.getText(0)) } }
        assertEquals(setOf("conversations", "messages"), tables)
    }

    @Test
    fun migratesVersionTwoToThreeKeepingEveryConversationAndMessageWithoutAPersona() = runTest {
        helper.createDatabase(version = 2).use { connection ->
            connection.execSQL(
                "INSERT INTO conversations (id, title, modelId, createdAt, updatedAt) " +
                    "VALUES (1, 'Postgres index', 'gpt5', 10, 20)",
            )
            connection.execSQL(
                "INSERT INTO messages " +
                    "(id, conversationId, role, text, blocks, modelId, status, latencyMillis, createdAt) " +
                    "VALUES (1, 1, 'user', 'why?', '[]', NULL, 'done', NULL, 10), " +
                    "(2, 1, 'assistant', '', '[{\"type\":\"p\",\"text\":\"ok\"}]', 'gpt5', 'done', 800, 10)",
            )
        }

        val connection = helper.runMigrationsAndValidate(version = 3)

        assertEquals(listOf("1|Postgres index|gpt5|10|20|null"), connection.rows("SELECT * FROM conversations"))
        assertEquals(
            listOf("1|user|null", "2|assistant|null"),
            connection.rows("SELECT id, role, personaId FROM messages ORDER BY id"),
        )
        assertEquals(listOf("0"), connection.rows("SELECT COUNT(*) FROM personas"))
        assertFalse(connection.rows("PRAGMA foreign_key_list(conversations)").any())
    }

    @Test
    fun migratesVersionThreeToFourKeepingEveryConversationNeitherPinnedNorArchived() = runTest {
        helper.createDatabase(version = 3).use { connection ->
            connection.execSQL(
                "INSERT INTO conversations (id, title, modelId, createdAt, updatedAt, personaId) " +
                    "VALUES (1, 'Postgres index', 'gpt5', 10, 20, 'terse'), (2, 'Hello', 'sonnet', 30, 40, NULL)",
            )
            connection.execSQL(
                "INSERT INTO messages " +
                    "(id, conversationId, role, text, blocks, modelId, status, latencyMillis, createdAt, personaId) " +
                    "VALUES (1, 1, 'user', 'why?', '[]', NULL, 'done', NULL, 10, NULL), " +
                    "(2, 1, 'assistant', '', '[{\"type\":\"p\",\"text\":\"ok\"}]', 'gpt5', 'done', 800, 10, 'terse')",
            )
        }

        val connection = helper.runMigrationsAndValidate(version = 4)

        assertEquals(
            listOf("1|Postgres index|gpt5|10|20|terse|0|0", "2|Hello|sonnet|30|40|null|0|0"),
            connection.rows("SELECT * FROM conversations ORDER BY id"),
        )
        assertEquals(
            listOf("1|user|null", "2|assistant|terse"),
            connection.rows("SELECT id, role, personaId FROM messages ORDER BY id"),
        )
    }

    @Test
    fun migratesVersionFourToFiveKeepingEveryConversationAsNoForkWithItsBannerNotDismissed() = runTest {
        helper.createDatabase(version = 4).use { connection ->
            connection.execSQL(
                "INSERT INTO conversations (id, title, modelId, createdAt, updatedAt, personaId, pinned, archived) " +
                    "VALUES (1, 'Postgres index', 'gpt5', 10, 20, 'terse', 1, 0), " +
                    "(2, 'Hello', 'sonnet', 30, 40, NULL, 0, 1)",
            )
            connection.execSQL(
                "INSERT INTO messages " +
                    "(id, conversationId, role, text, blocks, modelId, status, latencyMillis, createdAt, personaId) " +
                    "VALUES (1, 1, 'user', 'why?', '[]', NULL, 'done', NULL, 10, NULL), " +
                    "(2, 1, 'assistant', '', '[{\"type\":\"p\",\"text\":\"ok\"}]', 'gpt5', 'done', 800, 10, 'terse')",
            )
        }

        val connection = helper.runMigrationsAndValidate(version = 5)

        assertEquals(
            listOf(
                "1|Postgres index|gpt5|10|20|terse|1|0|null|null|0",
                "2|Hello|sonnet|30|40|null|0|1|null|null|0",
            ),
            connection.rows(
                "SELECT id, title, modelId, createdAt, updatedAt, personaId, pinned, archived, " +
                    "forkedFromTitle, forkedAtMessage, forkBannerDismissed FROM conversations ORDER BY id",
            ),
        )
        assertEquals(
            listOf("1|user|null", "2|assistant|terse"),
            connection.rows("SELECT id, role, personaId FROM messages ORDER BY id"),
        )
    }

    @Test
    fun migratesVersionFiveToSixKeepingEveryConversationNotComparingAndEveryMessageOnNoPairSide() = runTest {
        helper.createDatabase(version = 5).use { connection ->
            connection.execSQL(
                "INSERT INTO conversations (id, title, modelId, createdAt, updatedAt, personaId, pinned, archived, " +
                    "forkedFromTitle, forkedAtMessage, forkBannerDismissed) " +
                    "VALUES (1, 'Postgres index', 'gpt5', 10, 20, 'terse', 1, 0, NULL, NULL, 0), " +
                    "(2, 'Postgres index', 'gpt5', 30, 40, NULL, 0, 1, 'Postgres index', 2, 1)",
            )
            connection.execSQL(
                "INSERT INTO messages " +
                    "(id, conversationId, role, text, blocks, modelId, status, latencyMillis, createdAt, personaId) " +
                    "VALUES (1, 1, 'user', 'why?', '[]', NULL, 'done', NULL, 10, NULL), " +
                    "(2, 1, 'assistant', '', '[{\"type\":\"p\",\"text\":\"ok\"}]', 'gpt5', 'done', 800, 10, 'terse')",
            )
        }

        val connection = helper.runMigrationsAndValidate(version = 6)

        assertEquals(
            listOf(
                "1|Postgres index|gpt5|10|20|terse|1|0|null|null|0|0|null",
                "2|Postgres index|gpt5|30|40|null|0|1|Postgres index|2|1|0|null",
            ),
            connection.rows(
                "SELECT id, title, modelId, createdAt, updatedAt, personaId, pinned, archived, " +
                    "forkedFromTitle, forkedAtMessage, forkBannerDismissed, compareOn, compareModelId " +
                    "FROM conversations ORDER BY id",
            ),
        )
        assertEquals(
            listOf("1|user|null|null", "2|assistant|terse|null"),
            connection.rows("SELECT id, role, personaId, pairSide FROM messages ORDER BY id"),
        )
    }

    /** Every row of [sql], its columns joined by `|`, a NULL as `null`. */
    private fun SQLiteConnection.rows(sql: String): List<String> = prepare(sql).use { statement ->
        buildList {
            while (statement.step()) {
                add(
                    (0 until statement.getColumnCount()).joinToString("|") { column ->
                        if (statement.isNull(column)) "null" else statement.getText(column)
                    },
                )
            }
        }
    }
}
