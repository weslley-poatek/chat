package br.com.weslleycampos.chat.feature.history

import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryEntryProviderTest {
    private val json = Json { serializersModule = HistoryEntryProvider().serializerModule() }

    @Test
    fun aSavedBackStackRestoresHistory() {
        val saved = json.encodeToString(PolymorphicSerializer(NavKey::class), HistoryEntry)

        assertEquals<NavKey>(HistoryEntry, json.decodeFromString(PolymorphicSerializer(NavKey::class), saved))
    }
}
