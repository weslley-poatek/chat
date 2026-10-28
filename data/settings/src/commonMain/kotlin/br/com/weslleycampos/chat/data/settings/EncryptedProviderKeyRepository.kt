package br.com.weslleycampos.chat.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import br.com.weslleycampos.chat.data.settings.models.KeyMask
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.data.settings.models.ProviderKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [ProviderKeyRepository] on the app's encrypted DataStore: one string preference per provider,
 * [keyOf], holding the trimmed key. Reads mask it before it leaves this class.
 *
 * When the encrypted store recovers from a lost key or a corrupt file it empties itself, and every
 * provider then reads as not set; the user types their keys again.
 */
internal class EncryptedProviderKeyRepository(
    private val preferences: DataStore<Preferences>,
) : ProviderKeyRepository {
    override fun observeKeys(): Flow<List<ProviderKey>> = preferences.data
        .map { stored -> ModelProvider.entries.map { ProviderKey(it, stored[keyOf(it)]?.let(KeyMask::of)) } }
        .distinctUntilChanged()

    override suspend fun save(provider: ModelProvider, key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return false
        preferences.edit { it[keyOf(provider)] = trimmed }
        return true
    }

    override suspend fun remove(provider: ModelProvider): Boolean {
        val key = keyOf(provider)
        var removed = false
        preferences.edit { stored ->
            removed = key in stored
            stored.remove(key)
        }
        return removed
    }

    override suspend fun removeAll() {
        preferences.edit { stored -> ModelProvider.entries.forEach { stored.remove(keyOf(it)) } }
    }

    companion object {
        /** The preference [provider]'s key is stored under: `provider_key_<id>`. */
        fun keyOf(provider: ModelProvider): Preferences.Key<String> =
            stringPreferencesKey("provider_key_${provider.id}")
    }
}
