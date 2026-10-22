package br.com.weslleycampos.chat.core.database

import androidx.room3.useReaderConnection
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class ExportedSchemaTest {
    private val database = inMemoryChatDatabase()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun currentVersionIsExportedWithTheCompiledIdentityHash() = runTest {
        val (version, identityHash) = database.useReaderConnection { connection ->
            val version = connection.usePrepared("PRAGMA user_version") { statement ->
                statement.step()
                statement.getLong(0).toInt()
            }
            val identityHash = connection.usePrepared("SELECT identity_hash FROM room_master_table") { statement ->
                statement.step()
                statement.getText(0)
            }
            version to identityHash
        }

        val schema = assertNotNull(readExportedSchema(version), "No exported schema for version $version")
        assertContains(schema, "\"identityHash\": \"$identityHash\"")
    }
}
