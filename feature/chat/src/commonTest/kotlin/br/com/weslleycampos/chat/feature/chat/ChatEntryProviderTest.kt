package br.com.weslleycampos.chat.feature.chat

import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatEntryProviderTest {
    private val json = Json { serializersModule = ChatEntryProvider().serializerModule() }

    @Test
    fun aSavedBackStackRestoresANewChatAndAnOpenConversation() {
        for (entry in listOf(ChatEntry(), ChatEntry(conversationId = 42))) {
            val saved = json.encodeToString(PolymorphicSerializer(NavKey::class), entry)

            assertEquals<NavKey>(entry, json.decodeFromString(PolymorphicSerializer(NavKey::class), saved))
        }
    }
}
