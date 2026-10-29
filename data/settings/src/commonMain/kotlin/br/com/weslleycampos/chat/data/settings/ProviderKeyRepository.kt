package br.com.weslleycampos.chat.data.settings

import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.data.settings.models.ProviderKey
import kotlinx.coroutines.flow.Flow

/**
 * The user's own API key for each [ModelProvider], kept encrypted on this device and only ever
 * read back masked: nothing here returns a whole key. Only `FuelixKeySource` reads the Fuelix key
 * whole, for `:data:fuelix`'s requests, and no key is ever logged.
 */
interface ProviderKeyRepository {
    /**
     * Every provider, in [ModelProvider] order, with its key masked or `null` while it has none.
     * Emits the current rows as soon as it is collected and again whenever a row changes, never the
     * same rows twice in a row.
     */
    fun observeKeys(): Flow<List<ProviderKey>>

    /**
     * Stores [key], trimmed, as [provider]'s key, in place of any it had, as the edit row's "save"
     * does. Returns `true` when it stored it: the "key encrypted and stored" toast. A blank [key]
     * stores nothing and keeps the key the provider had, if any, and returns `false`.
     */
    suspend fun save(provider: ModelProvider, key: String): Boolean

    /** Removes [provider]'s key, as the edit row's "remove" does. Returns whether it had one. */
    suspend fun remove(provider: ModelProvider): Boolean

    /**
     * Removes every key stored, those of providers Settings no longer lists too, as deleting the
     * account does.
     */
    suspend fun removeAll()
}
