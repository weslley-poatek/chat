package br.com.weslleycampos.chat.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.core.Storage
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import br.com.weslleycampos.chat.core.preferences.qualifiers.UnencryptedPreferences
import br.com.weslleycampos.chat.core.preferences.qualifiers.UnencryptedStorage
import org.koin.core.annotation.Single

internal const val DATA_STORE_FILE_NAME = "chat.preferences_pb"

@Single
@UnencryptedPreferences
internal fun createPreferencesDataStore(
    @UnencryptedStorage storage: Storage<Preferences>,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    storage = storage,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
