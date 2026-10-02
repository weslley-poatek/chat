package br.com.weslleycampos.chat.core.preferences.encrypted

import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import br.com.weslleycampos.chat.core.preferences.preferencesDirectory
import br.com.weslleycampos.chat.core.preferences.qualifiers.EncryptedStorage
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single

// Explicit form required: Koin plugin 1.2.1 emits clashing hints for shorthand-qualified iosMain factories.
@Single
@Qualifier(EncryptedStorage::class)
internal fun createEncryptedPreferencesStorage(): Storage<Preferences> = OkioStorage(
    fileSystem = FileSystem.SYSTEM,
    serializer = EncryptingSerializer(
        PreferencesSerializer,
        IosKeychainAead,
        ENCRYPTED_DATA_STORE_FILE_NAME.encodeToByteArray(),
    ),
    producePath = { "${preferencesDirectory()}/$ENCRYPTED_DATA_STORE_FILE_NAME".toPath() },
)
