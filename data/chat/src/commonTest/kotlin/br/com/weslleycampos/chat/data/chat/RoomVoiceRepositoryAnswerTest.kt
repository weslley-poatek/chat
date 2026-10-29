package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakeFuelixClient
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
import br.com.weslleycampos.chat.data.chat.fakes.TestClock
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.data.chat.models.VoiceAnswer
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [RoomVoiceRepository.answer] while Fuelix is still answering: what happens to a turn that is
 * answered twice, followed, deleted or given another model before its reply arrives. On one fake
 * database and one fake Fuelix held mid-reply, on the test's virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomVoiceRepositoryAnswerTest {
    private val database = FakeChatDatabase()
    private val preferences = FakePreferences()
    private val fuelix = FakeFuelixClient()

    private val sonnet: ChatModel = ModelCatalogue.default
    private val gpt5: ChatModel = ModelCatalogue["gpt-5"]

    /** What the user is heard saying first. */
    private val heard = "So the debounce hook works, but I also want it to fire on the leading edge of a burst, " +
        "not just the trailing one."

    /** What the user is heard saying next. */
    private val heardNext = "And if the component unmounts while the timer is pending?"

    private class Repositories(val voice: RoomVoiceRepository, val chat: RoomChatRepository)

    /** Voice and chat on [database], [preferences] and [fuelix], as Koin wires them, in the test's scope. */
    private fun TestScope.repositories(): Repositories {
        val clock = TestClock(testScheduler)
        val models = LocalModelRepository(
            personaDao = database.personaDao,
            preferences = preferences,
            fuelix = fuelix,
            scope = this,
        )
        val chat = RoomChatRepository(
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
        val voice = RoomVoiceRepository(
            chats = chat,
            models = models,
            conversationDao = database.conversationDao,
            messageDao = database.messageDao,
            transactions = database.transactions,
            fuelix = fuelix,
            clock = clock,
        )
        return Repositories(voice = voice, chat = chat)
    }

    private fun answer(model: ChatModel): String = FakeFuelixClient.answer(model.id)

    @Test
    fun aTurnAnsweredTwiceAtOnceIsSavedOnceAndTheOtherAnswerIsDropped() = runTest {
        val voice = repositories().voice
        val turn = assertNotNull(voice.saveTranscript(null, heard))
        fuelix.hold(afterPieces = 0)

        val first = async { voice.answer(turn) }
        val second = async { voice.answer(turn) }
        advanceUntilIdle()
        fuelix.release()
        advanceUntilIdle()

        val answers = listOf(first.await(), second.await())
        assertEquals(1, answers.count { it == VoiceAnswer.Spoken(answer(sonnet)) })
        assertEquals(1, answers.count { it == VoiceAnswer.Dropped })
        assertEquals(1, database.messageRows.value.count { it.role == MessageRole.ASSISTANT })
        assertEquals(2, fuelix.calls.size)
    }

    @Test
    fun aReplyIsCreditedToTheModelThatWroteItEvenWhenAnotherIsPickedWhileItThinks() = runTest {
        val voice = repositories().voice
        val turn = assertNotNull(voice.saveTranscript(null, heard))
        fuelix.hold(afterPieces = 0)
        val answered = async { voice.answer(turn) }
        advanceUntilIdle()

        assertTrue(voice.choose(turn.conversationId, ModelChoice(gpt5)))
        fuelix.release()
        advanceUntilIdle()

        assertEquals(VoiceAnswer.Spoken(answer(sonnet)), answered.await())
        val reply = database.messageRows.value.single { it.role == MessageRole.ASSISTANT }
        assertEquals(sonnet.id, reply.modelId)
        assertEquals(gpt5.id, database.conversationRows.value.single().modelId)
    }

    @Test
    fun aTurnFollowedByATypedMessageOrDeletedWhileItIsAnsweredIsDroppedAndSavesNothing() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val chat = repositories.chat
        val followed = assertNotNull(voice.saveTranscript(null, heard))
        val deleted = assertNotNull(voice.saveTranscript(null, heardNext))
        fuelix.hold(afterPieces = 1)
        val followedAnswer = async { voice.answer(followed) }
        val deletedAnswer = async { voice.answer(deleted) }
        advanceUntilIdle()

        assertEquals(followed.conversationId, chat.send(followed.conversationId, "typed instead"))
        assertTrue(chat.delete(deleted.conversationId))
        fuelix.release()
        advanceUntilIdle()

        assertEquals(VoiceAnswer.Dropped, followedAnswer.await())
        assertEquals(VoiceAnswer.Dropped, deletedAnswer.await())
        val thread = chat.observeMessages(followed.conversationId).first()
        assertEquals(listOf(heard, "typed instead"), thread.filterIsInstance<Message.User>().map { it.text })
        assertEquals(ReplyStatus.DONE, assertIs<Message.Assistant>(thread.last()).status)
        assertEquals(1, database.messageRows.value.count { it.role == MessageRole.ASSISTANT })
    }
}
