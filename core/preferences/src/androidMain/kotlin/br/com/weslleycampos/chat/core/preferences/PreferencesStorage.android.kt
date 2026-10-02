package br.com.weslleycampos.chat.core.preferences

import android.content.Context
import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import br.com.weslleycampos.chat.core.preferences.qualifiers.UnencryptedStorage
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.koin.core.annotation.Single

@Single
@UnencryptedStorage
internal fun createPreferencesStorage(context: Context): Storage<Preferences> = OkioStorage(
    fileSystem = FileSystem.SYSTEM,
    serializer = PreferencesSerializer,
    producePath = { context.noBackupFilesDir.resolve(DATA_STORE_FILE_NAME).toOkioPath() },
)
