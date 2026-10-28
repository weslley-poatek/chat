package br.com.weslleycampos.chat.data.settings.fakes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A preferences DataStore in memory, standing in for the encrypted one. Like the real one, an
 * update applies atomically, and one that changes nothing writes nothing.
 */
internal class FakePreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    val stored = MutableStateFlow(initial)

    /** Updates that changed something. */
    var writes = 0
        private set

    private val updating = Mutex()

    override val data: Flow<Preferences> = stored

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        updating.withLock {
            val updated = transform(stored.value).toPreferences()
            if (updated != stored.value) {
                writes++
                stored.value = updated
            }
            updated
        }
}
