@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.core.database

import androidx.room3.useReaderConnection
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.plugin.module.dsl.module
import platform.Foundation.NSFileManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Opens the real on-disk database the way the app does. The test process is not the app, so its
 * Documents directory is the simulator's own rather than the app's container; the file is deleted
 * afterwards either way.
 */
class CoreDatabaseModuleTest {
    private val koin = koinApplication { module<CoreDatabaseModule>() }.koin

    @AfterTest
    fun deleteDatabase() {
        koin.get<ChatDatabase>().close()
        koin.close()
        val path = chatDatabasePath()
        listOf("", "-wal", "-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(path + it, error = null) }
    }

    @Test
    fun koinProvidesOneChatDatabaseThatOpensOnTheCurrentVersion() = runTest {
        val database = koin.get<ChatDatabase>()

        val version = database.useReaderConnection { connection ->
            connection.usePrepared("PRAGMA user_version") { statement ->
                statement.step()
                statement.getLong(0)
            }
        }

        assertSame(database, koin.get<ChatDatabase>())
        assertEquals(DATABASE_VERSION.toLong(), version)
    }
}
