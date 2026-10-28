package br.com.weslleycampos.chat.feature.settings

import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.entries.settings.SettingsEntry
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsEntryProviderTest {
    private val json = Json { serializersModule = SettingsEntryProvider().serializerModule() }

    @Test
    fun aSavedBackStackRestoresSettings() {
        val saved = json.encodeToString(PolymorphicSerializer(NavKey::class), SettingsEntry)

        assertEquals<NavKey>(SettingsEntry, json.decodeFromString(PolymorphicSerializer(NavKey::class), saved))
    }
}
