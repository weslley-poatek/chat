package br.com.weslleycampos.chat.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import br.com.weslleycampos.chat.data.fuelix.FuelixKeySource
import br.com.weslleycampos.chat.data.settings.EncryptedProviderKeyRepository.Companion.keyOf
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import kotlinx.coroutines.flow.first

/**
 * [FuelixKeySource] on the encrypted DataStore: the key [EncryptedProviderKeyRepository] saved for
 * [ModelProvider.FUELIX], whole, read for one request at a time. This is the only place a whole key
 * leaves the store.
 */
internal class EncryptedFuelixKeySource(private val preferences: DataStore<Preferences>) : FuelixKeySource {
    override suspend fun key(): String? = preferences.data.first()[keyOf(ModelProvider.FUELIX)]
}
