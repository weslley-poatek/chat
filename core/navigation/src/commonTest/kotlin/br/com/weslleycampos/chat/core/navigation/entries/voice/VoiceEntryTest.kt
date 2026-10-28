package br.com.weslleycampos.chat.core.navigation.entries.voice

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceEntryTest {
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) { subclass(VoiceEntry::class, VoiceEntry.serializer()) }
        }
    }

    @Test
    fun aNewSessionIsTheDefault() {
        assertNull(VoiceEntry().conversationId)
    }

    @Test
    fun aSavedBackStackRestoresANewSessionAndAConversationGoingOnInVoice() {
        for (entry in listOf(VoiceEntry(), VoiceEntry(conversationId = 42))) {
            val saved = json.encodeToString(PolymorphicSerializer(NavKey::class), entry)

            assertEquals<NavKey>(entry, json.decodeFromString(PolymorphicSerializer(NavKey::class), saved))
        }
    }
}
