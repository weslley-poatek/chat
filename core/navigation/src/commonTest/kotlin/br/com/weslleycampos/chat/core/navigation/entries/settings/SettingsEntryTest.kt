package br.com.weslleycampos.chat.core.navigation.entries.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsEntryTest {
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) { subclass(SettingsEntry::class, SettingsEntry.serializer()) }
        }
    }

    @Test
    fun aSavedBackStackRestoresSettings() {
        val saved = json.encodeToString(PolymorphicSerializer(NavKey::class), SettingsEntry)

        assertEquals<NavKey>(SettingsEntry, json.decodeFromString(PolymorphicSerializer(NavKey::class), saved))
    }
}
