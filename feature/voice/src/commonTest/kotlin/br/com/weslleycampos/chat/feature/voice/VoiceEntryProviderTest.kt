package br.com.weslleycampos.chat.feature.voice

import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceEntryProviderTest {
    private val json = Json { serializersModule = VoiceEntryProvider().serializerModule() }

    @Test
    fun aSavedBackStackRestoresANewSessionAndAConversationGoingOnInVoice() {
        for (entry in listOf(VoiceEntry(), VoiceEntry(conversationId = 42))) {
            val saved = json.encodeToString(PolymorphicSerializer(NavKey::class), entry)

            assertEquals<NavKey>(entry, json.decodeFromString(PolymorphicSerializer(NavKey::class), saved))
        }
    }
}
