package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
import br.com.weslleycampos.chat.data.chat.fakes.FixedRandom
import br.com.weslleycampos.chat.data.chat.fakes.TEST_START
import br.com.weslleycampos.chat.data.chat.fakes.TestClock
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.data.chat.models.VoiceSession
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import br.com.weslleycampos.chat.data.chat.replies.CannedReplies
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import br.com.weslleycampos.chat.data.chat.replies.VoiceLines
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Voice mode's data: [RoomVoiceRepository] next to the [RoomChatRepository] it reads through and the
 * [LocalModelRepository] it picks with, on one fake database. Each test plays the Voice screen's
 * phases by hand: [VoiceRepository.saveTranscript] when listening ends, [VoiceRepository.saveReply]
 * when thinking ends, on the test's virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomVoiceRepositoryTest {
    private var database = FakeChatDatabase()
    private val preferences = FakePreferences()

    private val lines: List<VoiceExchange> = VoiceLines.exchanges
    private val reviewer: Persona = PersonaPresets.personas.single { it.id == "reviewer" }
    private val terse: Persona = PersonaPresets.personas.single { it.id == "terse" }
    private val sonnet: ChatModel = ModelCatalogue["sonnet"]
    private val gpt5: ChatModel = ModelCatalogue["gpt5"]
    private val gemini: ChatModel = ModelCatalogue["gemini"]
    private val comparing = ModelChoice(model = sonnet, compareOn = true, compareWith = gpt5)

    /** The prototype's `USER_T`, cut to the 40 characters of a title. */
    private val firstTitle = "So the debounce hook works, but I also w"

    /** The thinking phase's length, in ms: what a reply's latency is when no tap skips it. */
    private val thinking = 2400L

    private class Repositories(
        val voice: RoomVoiceRepository,
        val chat: RoomChatRepository,
        val models: ModelRepository,
    )

    /**
     * Voice, chat and models on [database] and [preferences], as Koin wires them. Typed replies
     * stream at the slowest pace and shortest thinking time, in the test's own scope.
     */
    private fun TestScope.repositories(): Repositories {
        val clock = TestClock(testScheduler)
        val models = LocalModelRepository(personaDao = database.personaDao, preferences = preferences, scope = this)
        val chat = RoomChatRepository(
            conversationDao = database.conversationDao,
            historyDao = database.historyDao,
            messageDao = database.messageDao,
            compareDao = database.compareDao,
            transactions = database.transactions,
            models = models,
            streamer = ReplyStreamer(messageDao = database.messageDao, clock = clock, random = FixedRandom()),
            clock = clock,
            scope = this,
        )
        val voice = RoomVoiceRepository(
            chats = chat,
            models = models,
            conversationDao = database.conversationDao,
            messageDao = database.messageDao,
            transactions = database.transactions,
            clock = clock,
        )
        return Repositories(voice = voice, chat = chat, models = models)
    }

    /** One whole exchange as the screen plays it: listening ends, then thinking for [thinkingMillis]. */
    private suspend fun TestScope.exchange(
        voice: VoiceRepository,
        conversationId: Long?,
        thinkingMillis: Long = thinking,
    ): VoiceTurn {
        val turn = assertNotNull(voice.saveTranscript(conversationId))
        advanceTimeBy(thinkingMillis)
        assertTrue(voice.saveReply(turn))
        return turn
    }

    private suspend fun ChatRepository.conversation(id: Long): Conversation =
        assertNotNull(observeConversation(id).first())

    private fun userTexts(conversationId: Long): List<String> = database.messageRows.value
        .filter { it.conversationId == conversationId && it.role == MessageRole.USER }
        .map { it.text }

    @Test
    fun aNewSessionPlaysTheFirstExchangeOnTheDefaultModelFollowsTheDefaultAndSavesNothing() = runTest {
        val repositories = repositories()

        assertEquals(
            VoiceSession(conversationId = null, model = ModelCatalogue.default, next = lines[0]),
            repositories.voice.observeSession(null).first(),
        )
        repositories.models.setDefaultModel(gemini)

        assertEquals(
            VoiceSession(conversationId = null, model = gemini, next = lines[0]),
            repositories.voice.observeSession(null).first(),
        )
        assertTrue(database.conversationRows.value.isEmpty())
        assertTrue(database.messageRows.value.isEmpty())
    }

    @Test
    fun theFirstTranscriptCreatesAConversationTitledAfterItOnTheDefaultModel() = runTest {
        val repositories = repositories()
        val chat = repositories.chat
        repositories.models.setDefaultModel(gemini)

        val turn = assertNotNull(repositories.voice.saveTranscript(null))

        assertEquals(lines[0], turn.exchange)
        assertEquals(
            Conversation(
                id = turn.conversationId,
                title = firstTitle,
                model = gemini,
                createdAt = TEST_START,
                updatedAt = TEST_START,
            ),
            chat.conversation(turn.conversationId),
        )
        assertEquals(
            listOf(
                Message.User(
                    id = turn.transcriptId,
                    conversationId = turn.conversationId,
                    text = lines[0].transcript,
                    createdAt = TEST_START,
                ),
            ),
            chat.observeMessages(turn.conversationId).first(),
        )
    }

    @Test
    fun theFirstTranscriptStartsOnThePersonaOrModelPickedBeforeItWithoutCompareAndLeavesTheDefaultAlone() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val chat = repositories.chat

        val asReviewer = assertNotNull(voice.saveTranscript(null, newSession = ModelChoice(reviewer)))
        val compared = assertNotNull(
            voice.saveTranscript(null, newSession = ModelChoice(model = gemini, compareOn = true, compareWith = gpt5)),
        )

        assertEquals(ModelChoice(reviewer), chat.conversation(asReviewer.conversationId).choice)
        assertEquals(ModelChoice(gemini), chat.conversation(compared.conversationId).choice)
        assertFalse(database.conversationRows.value.single { it.id == compared.conversationId }.compareOn)
        assertEquals(ModelCatalogue.default, repositories.models.observeDefaultModel().first())
        assertEquals(0, preferences.writes)
    }

    @Test
    fun aNewSessionChoiceThatIsNotInTheCatalogueOrNotStoredSavesNothing() = runTest {
        val voice = repositories().voice

        assertNull(voice.saveTranscript(null, newSession = ModelChoice(gemini.copy(id = "gemini-1"))))
        assertNull(voice.saveTranscript(null, newSession = ModelChoice(terse.copy(id = "ghost"))))

        assertTrue(database.conversationRows.value.isEmpty())
        assertTrue(database.messageRows.value.isEmpty())
    }

    @Test
    fun theReplyIsSavedDoneAndWholeAsOneParagraphFromTheModelAndPersonaWithTheThinkingTimeAsItsLatency() = runTest {
        val repositories = repositories()
        val turn = assertNotNull(repositories.voice.saveTranscript(null, newSession = ModelChoice(reviewer)))
        advanceTimeBy(thinking)

        assertTrue(repositories.voice.saveReply(turn))

        val messages = repositories.chat.observeMessages(turn.conversationId).first()
        assertEquals(2, messages.size)
        assertEquals(
            Message.Assistant(
                id = messages.last().id,
                conversationId = turn.conversationId,
                model = reviewer.model,
                persona = reviewer,
                blocks = listOf(Block.Paragraph(lines[0].reply)),
                status = ReplyStatus.DONE,
                latency = thinking.milliseconds,
                createdAt = TEST_START,
            ),
            messages.last(),
        )
        assertEquals(TEST_START, repositories.chat.conversation(turn.conversationId).updatedAt)
        assertTrue(database.unfinishedMessages().isEmpty())
    }

    @Test
    fun aTapThatSkipsThinkingSavesTheReplyWithTheShorterThinkingTime() = runTest {
        val repositories = repositories()

        val turn = exchange(repositories.voice, conversationId = null, thinkingMillis = 900)

        val reply = repositories.chat.observeMessages(turn.conversationId).first().last()
        assertEquals(900.milliseconds, assertIs<Message.Assistant>(reply).latency)
    }

    @Test
    fun exchangesRotateThroughTheLinesAndTheSessionMovesOnAsSoonAsListeningEnds() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val id = exchange(voice, conversationId = null).conversationId

        val played = (1..lines.size).map {
            val next = assertNotNull(voice.observeSession(id).first()).next
            val turn = assertNotNull(voice.saveTranscript(id))
            assertEquals(next, turn.exchange)
            assertEquals(VoiceLines.next(exchangesSoFar = it + 1), voice.observeSession(id).first()?.next)
            advanceTimeBy(thinking)
            assertTrue(voice.saveReply(turn))
            turn.exchange
        }

        assertEquals(lines.drop(1) + lines[0], played)
        val thread = repositories.chat.observeMessages(id).first()
        assertEquals((lines + lines[0]).map { it.transcript }, thread.filterIsInstance<Message.User>().map { it.text })
        assertEquals(
            (lines + lines[0]).map { listOf(Block.Paragraph(it.reply)) },
            thread.filterIsInstance<Message.Assistant>().map { it.blocks },
        )
    }

    @Test
    fun theSessionEmitsWhenATranscriptIsSavedOrTheChoiceChangesButNotForTheReply() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val first = exchange(voice, conversationId = null)
        val sessions = mutableListOf<VoiceSession?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            voice.observeSession(first.conversationId).collect { sessions += it }
        }
        advanceUntilIdle()

        val turn = assertNotNull(voice.saveTranscript(first.conversationId))
        advanceUntilIdle()
        assertTrue(voice.saveReply(turn))
        advanceUntilIdle()
        assertTrue(voice.choose(first.conversationId, ModelChoice(reviewer)))
        advanceUntilIdle()

        val session = VoiceSession(
            conversationId = first.conversationId,
            model = ModelCatalogue.default,
            next = lines[1],
        )
        assertEquals(
            listOf<VoiceSession?>(
                session,
                session.copy(next = lines[2]),
                session.copy(model = reviewer.model, persona = reviewer, next = lines[2]),
            ),
            sessions,
        )
    }

    @Test
    fun aTypedConversationGoesOnInVoiceFromItsExchangesAndMovesToTheTopOfHistory() = runTest {
        val repositories = repositories()
        val chat = repositories.chat
        val typed = assertNotNull(chat.send(null, "why is my debounce stale?"))
        advanceUntilIdle()
        assertEquals(typed, chat.send(typed, "and the shell one?"))
        advanceUntilIdle()
        val newer = assertNotNull(chat.send(null, "newer"))
        advanceUntilIdle()
        assertEquals(listOf(newer, typed), chat.observeHistory().first().map { it.id })

        assertEquals(lines[2], repositories.voice.observeSession(typed).first()?.next)
        val turn = exchange(repositories.voice, conversationId = typed)

        assertEquals(typed, turn.conversationId)
        assertEquals(lines[2], turn.exchange)
        assertEquals(listOf(typed, newer), chat.observeHistory().first().map { it.id })
        val conversation = chat.conversation(typed)
        assertEquals("why is my debounce stale?", conversation.title)
        assertEquals(TEST_START + (currentTime - thinking).milliseconds, conversation.updatedAt)
        assertEquals(listOf("why is my debounce stale?", "and the shell one?", lines[2].transcript), userTexts(typed))
    }

    @Test
    fun aTranscriptInAnArchivedConversationBringsItBackToHistory() = runTest {
        val repositories = repositories()
        val id = exchange(repositories.voice, conversationId = null).conversationId
        assertTrue(repositories.chat.archive(id))
        assertTrue(repositories.chat.observeHistory().first().isEmpty())

        assertNotNull(repositories.voice.saveTranscript(id))

        assertEquals(listOf(id), repositories.chat.observeHistory().first().map { it.id })
        assertFalse(repositories.chat.conversation(id).archived)
    }

    @Test
    fun aVoiceConversationReopenedAfterARestartGoesOnWithTheRotation() = runTest {
        val before = repositories()
        val id = exchange(before.voice, conversationId = null).conversationId
        exchange(before.voice, conversationId = id)

        val after = repositories()

        assertEquals(lines[2], after.voice.observeSession(id).first()?.next)
        assertEquals(lines[2], assertNotNull(after.voice.saveTranscript(id)).exchange)
    }

    @Test
    fun aSessionEndedWhileThinkingKeepsTheTranscriptWithNoReplyAndTheConversationGoesOnFromIt() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val chat = repositories.chat
        val ended = assertNotNull(voice.saveTranscript(null))
        advanceTimeBy(1000)

        val id = ended.conversationId
        assertEquals(
            listOf(lines[0].transcript),
            chat.observeMessages(id).first().map { assertIs<Message.User>(it).text },
        )
        assertTrue(database.unfinishedMessages().isEmpty())
        assertEquals(listOf(id), chat.observeHistory().first().map { it.id })

        assertEquals(lines[1], voice.observeSession(id).first()?.next)
        val next = exchange(voice, conversationId = id)
        assertEquals(lines[1], next.exchange)
        assertFalse(voice.saveReply(ended))
        assertEquals(
            listOf(lines[0].transcript, lines[1].transcript, lines[1].reply),
            chat.observeMessages(id).first().map {
                when (it) {
                    is Message.User -> it.text
                    is Message.Assistant -> (it.blocks.single() as Block.Paragraph).text
                    is Message.ComparePair -> error("Voice never writes a pair")
                }
            },
        )
        assertEquals(id, chat.send(id, "typed after it"))
    }

    @Test
    fun aSessionEndedWhileSpeakingKeepsTheReplyWholeAndATurnIsAnsweredOnlyOnce() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val turn = assertNotNull(voice.saveTranscript(null))
        advanceTimeBy(thinking)
        assertTrue(voice.saveReply(turn))
        // Speaking has revealed a few words when the session ends; the reply is already saved whole.
        advanceTimeBy(1000)

        assertFalse(voice.saveReply(turn))

        val replies = repositories.chat.observeMessages(turn.conversationId).first()
            .filterIsInstance<Message.Assistant>()
        assertEquals(listOf(listOf(Block.Paragraph(lines[0].reply))), replies.map { it.blocks })
        assertEquals(ReplyStatus.DONE, replies.single().status)
        assertTrue(database.unfinishedMessages().isEmpty())
    }

    @Test
    fun aTurnIsNotAnsweredAfterATypedMessageOrOnceItsConversationIsGone() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val chat = repositories.chat
        val id = exchange(voice, conversationId = null).conversationId

        val overtaken = assertNotNull(voice.saveTranscript(id))
        assertEquals(id, chat.send(id, "typed instead"))
        advanceUntilIdle()
        assertFalse(voice.saveReply(overtaken))
        assertFalse(voice.saveReply(overtaken.copy(transcriptId = database.messageRows.value.last().id)))

        val orphan = assertNotNull(voice.saveTranscript(id))
        assertTrue(chat.delete(id))
        assertFalse(voice.saveReply(orphan))
        assertTrue(database.messageRows.value.isEmpty())
    }

    @Test
    fun aTranscriptWhileATypedReplyIsStillOnItsWaySavesNothing() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val id = assertNotNull(repositories.chat.send(null, "typed first"))

        assertNull(voice.saveTranscript(id))
        advanceTimeBy(400)
        assertEquals(MessageStatus.STREAMING, database.messageRows.value.last().status)
        assertNull(voice.saveTranscript(id))
        assertEquals(listOf("typed first"), userTexts(id))

        advanceUntilIdle()
        assertEquals(lines[1], assertNotNull(voice.saveTranscript(id)).exchange)
    }

    @Test
    fun aReplyAnEarlierRunLeftThinkingDoesNotHoldUpTheFirstTranscript() = runTest {
        database = FakeChatDatabase(
            conversations = listOf(
                ConversationEntity(id = 1, title = "left open", modelId = "gpt5", createdAt = 0, updatedAt = 0),
            ),
            messages = listOf(
                message(id = 1, role = MessageRole.USER, status = MessageStatus.DONE),
                message(id = 2, role = MessageRole.ASSISTANT, status = MessageStatus.THINKING),
            ),
        )
        val voice = repositories().voice

        val turn = assertNotNull(voice.saveTranscript(1))

        assertEquals(lines[1], turn.exchange)
        assertEquals(MessageStatus.INTERRUPTED, database.message(2).status)
    }

    @Test
    fun aConversationThatIsNotThereHasNoSessionAndSavesNothing() = runTest {
        val repositories = repositories()
        val voice = repositories.voice

        assertNull(voice.observeSession(42).first())
        assertNull(voice.saveTranscript(42))
        assertFalse(voice.saveReply(VoiceTurn(conversationId = 42, transcriptId = 1, exchange = lines[0])))
        assertTrue(database.conversationRows.value.isEmpty())

        val id = exchange(voice, conversationId = null).conversationId
        assertTrue(repositories.chat.delete(id))

        assertNull(voice.observeSession(id).first())
        assertNull(voice.saveTranscript(id))
        assertTrue(database.conversationRows.value.isEmpty())
    }

    @Test
    fun voiceConversationsAreListedInHistoryAndOpenAsTextLikeAnyOther() = runTest {
        val repositories = repositories()
        val chat = repositories.chat
        val id = exchange(repositories.voice, conversationId = null).conversationId

        assertEquals(listOf(firstTitle), chat.observeHistory().first().map { it.title })
        assertEquals(1, chat.observeConversationCount().first())
        assertEquals(listOf(id), chat.observeRecentConversations().first().map { it.id })
        assertEquals(listOf(id), chat.observeHistory("leading edge").first().map { it.id })
        val thread = chat.observeMessages(id).first()
        assertEquals(lines[0].transcript, assertIs<Message.User>(thread[0]).text)
        assertEquals(ReplyStatus.DONE, assertIs<Message.Assistant>(thread[1]).status)

        assertEquals(id, chat.send(id, "and in text?"))
        advanceUntilIdle()

        assertEquals(CannedReplies.replies[1], (chat.observeMessages(id).first().last() as Message.Assistant).blocks)
    }

    @Test
    fun choosingOnANewSessionMakesItsModelTheDefaultForNewSessionsAndChats() = runTest {
        val repositories = repositories()
        val voice = repositories.voice

        assertTrue(voice.choose(null, ModelChoice(gemini)))

        assertEquals(gemini, repositories.models.observeDefaultModel().first())
        assertEquals(gemini, voice.observeSession(null).first()?.model)
        assertTrue(database.conversationRows.value.isEmpty())
        val turn = exchange(voice, conversationId = null)
        assertEquals(gemini, repositories.chat.conversation(turn.conversationId).model)
        val typed = assertNotNull(repositories.chat.send(null, "a new chat"))
        assertEquals(gemini, repositories.chat.conversation(typed).model)

        assertTrue(voice.choose(null, ModelChoice(terse)))

        assertEquals(terse.model, repositories.models.observeDefaultModel().first())
        assertEquals(
            VoiceSession(conversationId = null, model = gpt5, next = lines[0]),
            voice.observeSession(null).first(),
        )
        val withPersona = assertNotNull(voice.saveTranscript(null, newSession = ModelChoice(terse)))
        assertEquals(ModelChoice(terse), repositories.chat.conversation(withPersona.conversationId).choice)
    }

    @Test
    fun choosingInAConversationChangesItsChoiceAndTheDefaultAndTurnsCompareOff() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val chat = repositories.chat
        val id = assertNotNull(chat.send(null, "compare these", newChat = comparing))
        advanceUntilIdle()
        val digest = PersonaPresets.personas.single { it.id == "digest" }

        assertTrue(voice.choose(id, ModelChoice(digest)))

        assertEquals(ModelChoice(digest), chat.conversation(id).choice)
        assertEquals(digest.model, repositories.models.observeDefaultModel().first())
        assertEquals(digest, voice.observeSession(id).first()?.persona)

        assertTrue(voice.choose(id, ModelChoice(model = gpt5, compareOn = true, compareWith = gemini)))

        assertEquals(ModelChoice(gpt5), chat.conversation(id).choice)
        assertEquals(gpt5, repositories.models.observeDefaultModel().first())
        assertEquals(ModelChoice(gpt5), voice.observeSession(id).first()?.choice)
    }

    @Test
    fun aChoiceThatIsNotInTheCatalogueNotStoredOrForAConversationThatIsNotThereChangesNothing() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val id = exchange(voice, conversationId = null).conversationId
        val retired = ModelChoice(gemini.copy(id = "gemini-1"))
        val ghost = ModelChoice(terse.copy(id = "ghost"))

        assertFalse(voice.choose(null, retired))
        assertFalse(voice.choose(null, ghost))
        assertFalse(voice.choose(id, retired))
        assertFalse(voice.choose(id, ghost))
        assertFalse(voice.choose(42, ModelChoice(gemini)))

        assertEquals(ModelCatalogue.default, repositories.models.observeDefaultModel().first())
        assertEquals(0, preferences.writes)
        assertEquals(ModelChoice(ModelCatalogue.default), repositories.chat.conversation(id).choice)
    }

    @Test
    fun aComparingConversationIsAnsweredInVoiceByItsModelAloneAndKeepsComparingInText() = runTest {
        val repositories = repositories()
        val chat = repositories.chat
        val id = assertNotNull(chat.send(null, "compare these", newChat = comparing))
        advanceUntilIdle()

        val session = assertNotNull(repositories.voice.observeSession(id).first())
        exchange(repositories.voice, conversationId = id)

        assertEquals(ModelChoice(sonnet), session.choice)
        assertNull(session.choice.comparing)
        val reply = assertIs<Message.Assistant>(chat.observeMessages(id).first().last())
        assertEquals(sonnet, reply.model)
        assertEquals(gpt5, chat.conversation(id).comparing)
    }

    @Test
    fun aTranscriptAndAReplyAreEachWrittenInOneTransaction() = runTest {
        val repositories = repositories()

        val id = exchange(repositories.voice, conversationId = null).conversationId
        exchange(repositories.voice, conversationId = id)

        // The conversation, the transcript and the reply; then the move to the top, the transcript and the reply.
        assertEquals(6, database.writes)
        assertEquals(database.writes, database.writesInTransactions)
    }

    private fun message(id: Long, role: MessageRole, status: MessageStatus) = MessageEntity(
        id = id,
        conversationId = 1,
        role = role,
        text = if (role == MessageRole.USER) "hi" else "",
        blocks = emptyList(),
        modelId = if (role == MessageRole.USER) null else "gpt5",
        status = status,
        latencyMillis = null,
        createdAt = 0,
    )
}
