package br.com.weslleycampos.chat.core.preferences

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencesDataStoreTest {
    private val fileSystem = FileSystem.SYSTEM
    private val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "${Random.nextLong()}.preferences_pb"
    private val key = stringPreferencesKey("key")

    @AfterTest
    fun deleteFile() {
        fileSystem.delete(path)
    }

    @Test
    fun corruptFileResetsToEmptyAndAcceptsWrites() = runTest {
        // A length-delimited field that claims more bytes than the file holds.
        fileSystem.write(path) { write(byteArrayOf(0x0A, 0x7F)) }
        val store = createPreferencesDataStore(OkioStorage(fileSystem, PreferencesSerializer) { path })

        assertEquals(emptyPreferences(), store.data.first())

        store.edit { it[key] = "value" }
        assertEquals("value", store.data.first()[key])
    }
}
