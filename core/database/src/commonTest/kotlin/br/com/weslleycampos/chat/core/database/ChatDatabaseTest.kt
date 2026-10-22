package br.com.weslleycampos.chat.core.database

import androidx.room3.useReaderConnection
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatDatabaseTest {
    private val database = inMemoryChatDatabase()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun inMemoryDatabaseOpensOnTheCurrentVersion() = runTest {
        val version = database.useReaderConnection { connection ->
            connection.usePrepared("PRAGMA user_version") { statement ->
                statement.step()
                statement.getLong(0)
            }
        }

        assertEquals(DATABASE_VERSION.toLong(), version)
    }

    @Test
    fun placeholderTableExistsAndIsEmpty() = runTest {
        val rows = database.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM placeholder") { statement ->
                statement.step()
                statement.getLong(0)
            }
        }

        assertEquals(0L, rows)
    }
}
