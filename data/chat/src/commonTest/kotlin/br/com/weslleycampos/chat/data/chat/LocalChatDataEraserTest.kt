package br.com.weslleycampos.chat.data.chat

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakeFuelixClient
import br.com.weslleycampos.chat.data.chat.fakes.FakeFuelixClient.Companion.PIECE_DELAY
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
import br.com.weslleycampos.chat.data.chat.fakes.TestClock
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Deleting the account's side of `:data:chat`: everything the chat keeps goes, the personas stay. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalChatDataEraserTest {
    private val database = FakeChatDatabase()

    /** A key another part of the app might keep in the same store; the eraser leaves it alone. */
    private val otherKey = stringPreferencesKey("not_the_chats")
    private val preferences = FakePreferences(preferencesOf(otherKey to "kept"))
    private val fuelix = FakeFuelixClient()

    private val eraser = LocalChatDataEraser(conversationDao = database.conversationDao, preferences = preferences)

    private data class Chat(val models: ModelRepository, val chats: ChatRepository)

    private fun TestScope.chat(): Chat {
        val clock = TestClock(testScheduler)
        val models = LocalModelRepository(
            personaDao = database.personaDao,
            preferences = preferences,
            fuelix = fuelix,
            scope = this,
        )
        val chats = RoomChatRepository(
            conversationDao = database.conversationDao,
            historyDao = database.historyDao,
            messageDao = database.messageDao,
            compareDao = database.compareDao,
            transactions = database.transactions,
            models = models,
            streamer = ReplyStreamer(messageDao = database.messageDao, fuelix = fuelix, clock = clock),
            clock = clock,
            scope = this,
        )
        return Chat(models, chats)
    }

    @Test
    fun erasingDeletesEveryConversationAndMessageTheChoicesAndTheKeysModelsButKeepsThePersonas() = runTest {
        val (models, chats) = chat()
        val first = assertNotNull(chats.send(null, "first"))
        val pinned = assertNotNull(chats.send(null, "pinned"))
        val archived = assertNotNull(chats.send(null, "archived"))
        advanceUntilIdle()
        chats.setPinned(pinned, pinned = true)
        chats.archive(archived)
        models.setFavorite(ModelCatalogue["claude-opus-4-1-20250805"], favorite = true)
        models.setDefaultModel(ModelCatalogue["gemini-2.5-pro"])
        fuelix.models = listOf("gpt-5", "claude-haiku-4-5-20251001")
        assertTrue(models.refreshModels())
        assertNotNull(preferences.stored.value[LocalModelRepository.ModelIdsKey])

        eraser.eraseAll()

        assertTrue(chats.observeHistory().first().isEmpty())
        assertEquals(0, chats.observeConversationCount().first())
        assertTrue(chats.observeRecentConversations().first().isEmpty())
        for (id in listOf(first, pinned, archived)) {
            assertNull(chats.observeConversation(id).first())
            assertTrue(chats.observeMessages(id).first().isEmpty())
        }
        assertTrue(database.messageRows.value.isEmpty())
        assertEquals(ModelRepository.DEFAULT_FAVORITE_IDS, models.observeFavorites().first().map { it.id })
        assertEquals(ModelCatalogue.default, models.observeDefaultModel().first())
        assertEquals(ModelCatalogue.models, models.observeModels().first())
        assertEquals(PersonaPresets.personas, models.observePersonas().first())
        assertEquals(preferencesOf(otherKey to "kept"), preferences.stored.value)
    }

    @Test
    fun erasingPutsTheVoiceLanguagesBackToTheDevicesAlone() = runTest {
        val languages = LocalVoiceLanguageRepository(preferences = preferences, deviceLanguage = { "pt-BR" })
        languages.setLanguages(VoiceLanguages(VoiceLanguage.ENGLISH_US, bilingual = true))

        eraser.eraseAll()

        assertEquals(VoiceLanguages(VoiceLanguage.PORTUGUESE_BRAZIL), languages.observeLanguages().first())
        assertEquals(preferencesOf(otherKey to "kept"), preferences.stored.value)
    }

    @Test
    fun erasingWhileAReplyStreamsStopsItAtItsNextWrite() = runTest {
        val chats = chat().chats
        val id = assertNotNull(chats.send(null, "first"))
        advanceTimeBy(PIECE_DELAY * 1.5)
        val reply = chats.observeMessages(id).first().filterIsInstance<Message.Assistant>().single()
        assertEquals(ReplyStatus.STREAMING, reply.status)
        val writesBefore = database.replyUpdates.count { it.id == reply.id }

        eraser.eraseAll()
        advanceUntilIdle()

        assertEquals(writesBefore + 1, database.replyUpdates.count { it.id == reply.id })
        assertTrue(database.messageRows.value.isEmpty())
        assertTrue(database.conversationRows.value.isEmpty())
    }

    @Test
    fun erasingAgainErasesNothingMoreAndTheNextChatStartsAfresh() = runTest {
        val chats = chat().chats
        assertNotNull(chats.send(null, "before"))
        advanceUntilIdle()

        eraser.eraseAll()
        val writes = database.writes
        val preferenceWrites = preferences.writes
        eraser.eraseAll()

        assertEquals(writes, database.writes)
        assertEquals(preferenceWrites, preferences.writes)
        val next = assertNotNull(chats.send(null, "after"))
        advanceUntilIdle()
        assertEquals(listOf("after"), chats.observeHistory().first().map(Conversation::title))
        assertEquals(ModelCatalogue.default, chats.observeConversation(next).first()?.model)
    }
}
