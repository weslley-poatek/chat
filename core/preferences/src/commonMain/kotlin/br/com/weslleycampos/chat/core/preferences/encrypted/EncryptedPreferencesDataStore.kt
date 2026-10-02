package br.com.weslleycampos.chat.core.preferences.encrypted

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import br.com.weslleycampos.chat.core.preferences.qualifiers.EncryptedPreferences
import br.com.weslleycampos.chat.core.preferences.qualifiers.EncryptedStorage
import org.koin.core.annotation.Single

internal const val ENCRYPTED_DATA_STORE_FILE_NAME = "chat.encrypted.preferences_pb"

@Single
@EncryptedPreferences
internal fun createEncryptedPreferencesDataStore(
    @EncryptedStorage storage: Storage<Preferences>,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    storage = storage,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
