package br.com.weslleycampos.chat.core.preferences.encrypted

import android.content.Context
import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import br.com.weslleycampos.chat.core.preferences.qualifiers.EncryptedStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.koin.core.annotation.Single

@Single
@EncryptedStorage
internal fun createEncryptedPreferencesStorage(context: Context): Storage<Preferences> = OkioStorage(
    fileSystem = FileSystem.SYSTEM,
    serializer = EncryptingSerializer(
        PreferencesSerializer,
        AndroidKeystoreAead,
        ENCRYPTED_DATA_STORE_FILE_NAME.encodeToByteArray(),
    ),
    producePath = { context.noBackupFilesDir.resolve(ENCRYPTED_DATA_STORE_FILE_NAME).toOkioPath() },
)
