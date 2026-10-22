@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
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
}
