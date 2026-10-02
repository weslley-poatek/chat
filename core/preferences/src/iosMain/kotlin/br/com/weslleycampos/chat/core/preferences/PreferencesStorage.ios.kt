package br.com.weslleycampos.chat.core.preferences

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import br.com.weslleycampos.chat.core.preferences.qualifiers.UnencryptedStorage
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single

// Explicit form required: Koin plugin 1.2.1 emits clashing hints for shorthand-qualified iosMain factories.
@Single
@Qualifier(UnencryptedStorage::class)
internal fun createPreferencesStorage(): Storage<Preferences> = OkioStorage(
    fileSystem = FileSystem.SYSTEM,
    serializer = PreferencesSerializer,
    producePath = { "${preferencesDirectory()}/$DATA_STORE_FILE_NAME".toPath() },
)
