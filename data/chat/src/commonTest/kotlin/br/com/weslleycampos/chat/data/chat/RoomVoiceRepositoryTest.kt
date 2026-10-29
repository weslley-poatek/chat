package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakeFuelixClient
import br.com.weslleycampos.chat.data.chat.fakes.FakeFuelixClient.Companion.PIECE_DELAY
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
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
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.data.chat.models.VoiceAnswer
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import br.com.weslleycampos.chat.data.chat.models.VoiceSession
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import br.com.weslleycampos.chat.data.chat.replies.ReplyPrompts
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import br.com.weslleycampos.chat.data.fuelix.FuelixException
import br.com.weslleycampos.chat.data.fuelix.FuelixMessage
import br.com.weslleycampos.chat.data.fuelix.FuelixRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
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
 * [LocalModelRepository] it picks with, on one fake database and one fake Fuelix. Each test plays
 * the Voice screen's phases by hand: [VoiceRepository.saveTranscript] with what was heard when
 * listening ends, then [VoiceRepository.answer] while thinking, on the test's virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomVoiceRepositoryTest {
    private var database = FakeChatDatabase()
    private val preferences = FakePreferences()
    private val fuelix = FakeFuelixClient()

    /** The voice language on [preferences], on an English device until a test picks another. */
    private val languages = LocalVoiceLanguageRepository(preferences = preferences, deviceLanguage = { "en-US" })

    /** Voice mode's system prompt in [languages]' English. */
    private val voicePrompt = ReplyPrompts.voicePrompt(VoiceLanguages(VoiceLanguage.ENGLISH_US))

    private val reviewer: Persona = PersonaPresets.personas.single { it.id == "reviewer" }
    private val terse: Persona = PersonaPresets.personas.single { it.id == "terse" }
    private val sonnet: ChatModel = ModelCatalogue.default
    private val gpt5: ChatModel = ModelCatalogue["gpt-5"]
    private val gemini: ChatModel = ModelCatalogue["gemini-2.5-pro"]
    private val comparing = ModelChoice(model = sonnet, compareOn = true, compareWith = gpt5)

    /** What the user is heard saying first: the prototype's `USER_T`. */
    private val heard = "So the debounce hook works, but I also want it to fire on the leading edge of a burst, " +
        "not just the trailing one."

    /** What the user is heard saying next. */
    private val heardNext = "And if the component unmounts while the timer is pending?"

    /** [heard] cut to the 40 characters of a title. */
    private val firstTitle = "So the debounce hook works, but I also w"

    /** How long a default answer takes to come whole: its four pieces, 50 ms apart. */
    private val answering = PIECE_DELAY * FakeFuelixClient.DEFAULT_PIECES

    private class Repositories(
        val voice: RoomVoiceRepository,
        val chat: RoomChatRepository,
        val models: ModelRepository,
    )

    /** Voice, chat and models on [database], [preferences] and [fuelix], as Koin wires them, in the test's scope. */
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
            languages = languages,
            clock = clock,
        )
        return Repositories(voice = voice, chat = chat, models = models)
    }

    /** One whole exchange as the screen plays it: listening ends on [transcript], then thinking until its answer. */
    private suspend fun exchange(voice: VoiceRepository, conversationId: Long?, transcript: String = heard): VoiceTurn {
        val turn = assertNotNull(voice.saveTranscript(conversationId, transcript))
        assertIs<VoiceAnswer.Spoken>(voice.answer(turn))
        return turn
    }

    private suspend fun ChatRepository.conversation(id: Long): Conversation =
        assertNotNull(observeConversation(id).first())

    private fun userTexts(conversationId: Long): List<String> = database.messageRows.value
        .filter { it.conversationId == conversationId && it.role == MessageRole.USER }
        .map { it.text }

    /** What [model] answers when nothing else is scripted for it. */
    private fun answer(model: ChatModel): String = FakeFuelixClient.answer(model.id)

    private fun asUser(text: String) = FuelixMessage(FuelixRole.USER, text)

    @Test
    fun aNewSessionIsOnTheDefaultModelFollowsTheDefaultAndSavesNothing() = runTest {
        val repositories = repositories()

        assertEquals(
            VoiceSession(conversationId = null, model = ModelCatalogue.default),
            repositories.voice.observeSession(null).first(),
        )
        repositories.models.setDefaultModel(gemini)

        assertEquals(
            VoiceSession(conversationId = null, model = gemini),
            repositories.voice.observeSession(null).first(),
        )
        assertTrue(database.conversationRows.value.isEmpty())
        assertTrue(database.messageRows.value.isEmpty())
    }

    @Test
    fun theFirstTranscriptIsSavedTrimmedInAConversationTitledAfterItOnTheDefaultModel() = runTest {
        val repositories = repositories()
        val chat = repositories.chat
        repositories.models.setDefaultModel(gemini)

        val turn = assertNotNull(repositories.voice.saveTranscript(null, "  $heard \n"))

        assertEquals(VoiceExchange(transcript = heard), turn.exchange)
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
                    text = heard,
                    createdAt = TEST_START,
                ),
            ),
            chat.observeMessages(turn.conversationId).first(),
        )
    }

    @Test
    fun aBlankTranscriptSavesNothing() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val id = exchange(voice, conversationId = null).conversationId
        val updatedAt = repositories.chat.conversation(id).updatedAt
        advanceTimeBy(1000)
        val writes = database.writes

        assertNull(voice.saveTranscript(null, ""))
        assertNull(voice.saveTranscript(null, " \n\t"))
        assertNull(voice.saveTranscript(id, "  "))

        assertEquals(writes, database.writes)
        assertEquals(listOf(id), database.conversationRows.value.map { it.id })
        assertEquals(updatedAt, repositories.chat.conversation(id).updatedAt)
    }

    @Test
    fun theFirstTranscriptStartsOnThePersonaOrModelPickedBeforeItWithoutCompareAndLeavesTheDefaultAlone() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val chat = repositories.chat

        val asReviewer = assertNotNull(voice.saveTranscript(null, heard, newSession = ModelChoice(reviewer)))
        val compared = assertNotNull(
            voice.saveTranscript(
                null,
                heard,
                newSession = ModelChoice(model = gemini, compareOn = true, compareWith = gpt5),
            ),
        )

        assertEquals(ModelChoice(reviewer), chat.conversation(asReviewer.conversationId).choice)
        assertEquals(ModelChoice(gemini), chat.conversation(compared.conversationId).choice)
        assertFalse(database.conversationRows.value.single { it.id == compared.conversationId }.compareOn)
        assertEquals(ModelCatalogue.default, repositories.models.observeDefaultModel().first())
        assertEquals(0, preferences.writes)
    }

    @Test
    fun aNewSessionChoiceWithABlankModelOrAPersonaThatIsNotStoredSavesNothing() = runTest {
        val voice = repositories().voice

        assertNull(voice.saveTranscript(null, heard, newSession = ModelChoice(gemini.copy(id = " "))))
        assertNull(voice.saveTranscript(null, heard, newSession = ModelChoice(terse.copy(id = "ghost"))))

        assertTrue(database.conversationRows.value.isEmpty())
        assertTrue(database.messageRows.value.isEmpty())
    }

    @Test
    fun theAnswerIsSavedDoneAndWholeFromTheModelAndPersonaWithTheTimeItTookAsItsLatency() = runTest {
        val repositories = repositories()
        val turn = assertNotNull(repositories.voice.saveTranscript(null, heard, newSession = ModelChoice(reviewer)))
        fuelix.reply(reviewer.model.id, "Add a leading ", "flag.")

        val answer = repositories.voice.answer(turn)

        assertEquals(VoiceAnswer.Spoken("Add a leading flag."), answer)
        val messages = repositories.chat.observeMessages(turn.conversationId).first()
        assertEquals(2, messages.size)
        assertEquals(
            Message.Assistant(
                id = messages.last().id,
                conversationId = turn.conversationId,
                model = reviewer.model,
                persona = reviewer,
                blocks = listOf(Block.Paragraph("Add a leading flag.")),
                status = ReplyStatus.DONE,
                latency = PIECE_DELAY * 2,
                createdAt = TEST_START,
            ),
            messages.last(),
        )
        assertEquals(TEST_START, repositories.chat.conversation(turn.conversationId).updatedAt)
        assertTrue(database.unfinishedMessages().isEmpty())
    }

    @Test
    fun theAnswerAsksTheConversationsModelWithTheVoicePromptThePersonasPromptAndTheConversationSoFar() = runTest {
        val voice = repositories().voice
        exchange(voice, conversationId = null)
        val asReviewer = assertNotNull(voice.saveTranscript(null, heard, newSession = ModelChoice(reviewer)))
        voice.answer(asReviewer)
        exchange(voice, conversationId = asReviewer.conversationId, transcript = heardNext)

        val (alone, persona, followUp) = fuelix.calls
        assertEquals(sonnet.id, alone.model)
        assertEquals(listOf(FuelixMessage(FuelixRole.SYSTEM, voicePrompt), asUser(heard)), alone.messages)
        val prompt = FuelixMessage(FuelixRole.SYSTEM, voicePrompt + "\n\n" + reviewer.prompt)
        assertEquals(reviewer.model.id, persona.model)
        assertEquals(listOf(prompt, asUser(heard)), persona.messages)
        assertEquals(
            listOf(
                prompt,
                asUser(heard),
                FuelixMessage(FuelixRole.ASSISTANT, answer(reviewer.model)),
                asUser(heardNext),
            ),
            followUp.messages,
        )
    }

    @Test
    fun theSpokenAnswerHasItsWordsOneSpaceApartAndIsSavedAsTheModelWroteIt() = runTest {
        val repositories = repositories()
        val turn = assertNotNull(repositories.voice.saveTranscript(null, heard))
        fuelix.reply(sonnet.id, "  Add a  leading\n", "flag,\tthen wait.  ")

        val answer = repositories.voice.answer(turn)

        assertEquals(VoiceAnswer.Spoken("Add a leading flag, then wait."), answer)
        val reply = assertIs<Message.Assistant>(repositories.chat.observeMessages(turn.conversationId).first().last())
        assertEquals(listOf(Block.Paragraph("Add a  leading\nflag,\tthen wait.")), reply.blocks)
    }

    @Test
    fun aFailedAnswerSavesNothingAndTheTurnCanBeAnsweredAgain() = runTest {
        val voice = repositories().voice
        val turn = assertNotNull(voice.saveTranscript(null, heard))
        fuelix.reply(sonnet.id, failure = FuelixException.MissingKey())
        fuelix.reply(sonnet.id, "Add a ", failure = FuelixException.KeyRefused())

        assertEquals(VoiceAnswer.Failed(ReplyFailure.NO_KEY), voice.answer(turn))
        assertEquals(VoiceAnswer.Failed(ReplyFailure.KEY_REFUSED), voice.answer(turn))

        assertEquals(listOf(heard), userTexts(turn.conversationId))
        assertEquals(1, database.messageRows.value.size)
        assertTrue(database.unfinishedMessages().isEmpty())
        assertEquals(VoiceAnswer.Spoken(answer(sonnet)), voice.answer(turn))
        assertEquals(2, database.messageRows.value.size)
    }

    @Test
    fun anEmptyAnswerFailsAndSavesNothing() = runTest {
        val voice = repositories().voice
        val turn = assertNotNull(voice.saveTranscript(null, heard))
        fuelix.reply(sonnet.id)
        fuelix.reply(sonnet.id, " ", "\n\t")

        assertEquals(VoiceAnswer.Failed(ReplyFailure.FAILED), voice.answer(turn))
        assertEquals(VoiceAnswer.Failed(ReplyFailure.FAILED), voice.answer(turn))

        assertEquals(1, database.messageRows.value.size)
    }

    @Test
    fun anAnswerCancelledWhileThinkingSavesNothingAndLeavesNothingUnfinished() = runTest {
        val voice = repositories().voice
        val turn = assertNotNull(voice.saveTranscript(null, heard))
        val thinking = launch { voice.answer(turn) }
        advanceTimeBy(PIECE_DELAY * 2.5)

        thinking.cancel()
        runCurrent()

        assertEquals(listOf(heard), userTexts(turn.conversationId))
        assertEquals(1, database.messageRows.value.size)
        assertTrue(database.unfinishedMessages().isEmpty())
        assertEquals(VoiceAnswer.Spoken(answer(sonnet)), voice.answer(turn))
    }

    @Test
    fun eachTranscriptIsSavedAsHeardAndAnsweredInTurn() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val transcripts = listOf(heard, heardNext, "Last thing. How would you test it?")
        val id = exchange(voice, conversationId = null, transcript = transcripts[0]).conversationId

        val turns = transcripts.drop(1).map { exchange(voice, conversationId = id, transcript = it) }

        assertEquals(transcripts.drop(1).map { VoiceExchange(transcript = it) }, turns.map { it.exchange })
        assertEquals(List(2) { id }, turns.map { it.conversationId })
        val thread = repositories.chat.observeMessages(id).first()
        assertEquals(transcripts, thread.filterIsInstance<Message.User>().map { it.text })
        assertEquals(
            List(transcripts.size) { listOf(Block.Paragraph(answer(sonnet))) },
            thread.filterIsInstance<Message.Assistant>().map { it.blocks },
        )
        assertEquals(listOf(firstTitle), repositories.chat.observeHistory().first().map { it.title })
    }

    @Test
    fun theSessionEmitsWhenTheChoiceChangesButNotForATranscriptOrAnAnswer() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val first = exchange(voice, conversationId = null)
        val sessions = mutableListOf<VoiceSession?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            voice.observeSession(first.conversationId).collect { sessions += it }
        }
        advanceUntilIdle()

        val turn = assertNotNull(voice.saveTranscript(first.conversationId, heardNext))
        advanceUntilIdle()
        assertIs<VoiceAnswer.Spoken>(voice.answer(turn))
        advanceUntilIdle()
        assertTrue(voice.choose(first.conversationId, ModelChoice(reviewer)))
        advanceUntilIdle()

        val session = VoiceSession(conversationId = first.conversationId, model = ModelCatalogue.default)
        assertEquals(
            listOf<VoiceSession?>(session, session.copy(model = reviewer.model, persona = reviewer)),
            sessions,
        )
    }

    @Test
    fun aTypedConversationGoesOnInVoiceAndMovesToTheTopOfHistory() = runTest {
        val repositories = repositories()
        val chat = repositories.chat
        val typed = assertNotNull(chat.send(null, "why is my debounce stale?"))
        advanceUntilIdle()
        assertEquals(typed, chat.send(typed, "and the shell one?"))
        advanceUntilIdle()
        val newer = assertNotNull(chat.send(null, "newer"))
        advanceUntilIdle()
        assertEquals(listOf(newer, typed), chat.observeHistory().first().map { it.id })

        val session = repositories.voice.observeSession(typed).first()
        assertEquals(VoiceSession(conversationId = typed, model = sonnet), session)
        val turn = assertNotNull(repositories.voice.saveTranscript(typed, heard))
        val listeningEnded = currentTime
        assertIs<VoiceAnswer.Spoken>(repositories.voice.answer(turn))

        assertEquals(typed, turn.conversationId)
        assertEquals(VoiceExchange(transcript = heard), turn.exchange)
        assertEquals(listOf(typed, newer), chat.observeHistory().first().map { it.id })
        val conversation = chat.conversation(typed)
        assertEquals("why is my debounce stale?", conversation.title)
        assertEquals(TEST_START + listeningEnded.milliseconds, conversation.updatedAt)
        assertEquals(listOf("why is my debounce stale?", "and the shell one?", heard), userTexts(typed))
        assertEquals(
            listOf(
                FuelixMessage(FuelixRole.SYSTEM, voicePrompt),
                asUser("why is my debounce stale?"),
                FuelixMessage(FuelixRole.ASSISTANT, answer(sonnet)),
                asUser("and the shell one?"),
                FuelixMessage(FuelixRole.ASSISTANT, answer(sonnet)),
                asUser(heard),
            ),
            fuelix.calls.last().messages,
        )
    }

    @Test
    fun aTranscriptInAnArchivedConversationBringsItBackToHistory() = runTest {
        val repositories = repositories()
        val id = exchange(repositories.voice, conversationId = null).conversationId
        assertTrue(repositories.chat.archive(id))
        assertTrue(repositories.chat.observeHistory().first().isEmpty())

        assertNotNull(repositories.voice.saveTranscript(id, heardNext))

        assertEquals(listOf(id), repositories.chat.observeHistory().first().map { it.id })
        assertFalse(repositories.chat.conversation(id).archived)
    }

    @Test
    fun aVoiceConversationReopenedAfterARestartGoesOnFromItsExchanges() = runTest {
        val before = repositories()
        val id = exchange(before.voice, conversationId = null).conversationId

        val after = repositories()

        assertEquals(VoiceSession(conversationId = id, model = sonnet), after.voice.observeSession(id).first())
        exchange(after.voice, conversationId = id, transcript = heardNext)
        assertEquals(
            listOf(asUser(heard), FuelixMessage(FuelixRole.ASSISTANT, answer(sonnet)), asUser(heardNext)),
            fuelix.calls.last().messages.drop(1),
        )
    }

    @Test
    fun aSessionEndedWhileThinkingKeepsTheTranscriptWithNoReplyAndTheConversationGoesOnFromIt() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val chat = repositories.chat
        val ended = assertNotNull(voice.saveTranscript(null, heard))
        advanceTimeBy(1000)

        val id = ended.conversationId
        assertEquals(listOf(heard), chat.observeMessages(id).first().map { assertIs<Message.User>(it).text })
        assertTrue(database.unfinishedMessages().isEmpty())
        assertEquals(listOf(id), chat.observeHistory().first().map { it.id })

        exchange(voice, conversationId = id, transcript = heardNext)
        assertEquals(VoiceAnswer.Dropped, voice.answer(ended))
        assertEquals(
            listOf(heard, heardNext, answer(sonnet)),
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
        val turn = exchange(voice, conversationId = null)
        // Speaking has revealed a few words when the session ends; the reply is already saved whole.
        advanceTimeBy(1000)
        val calls = fuelix.calls.size

        assertEquals(VoiceAnswer.Dropped, voice.answer(turn))

        val replies = repositories.chat.observeMessages(turn.conversationId).first()
            .filterIsInstance<Message.Assistant>()
        assertEquals(listOf(listOf(Block.Paragraph(answer(sonnet)))), replies.map { it.blocks })
        assertEquals(ReplyStatus.DONE, replies.single().status)
        assertEquals(answering, replies.single().latency)
        assertTrue(database.unfinishedMessages().isEmpty())
        assertEquals(calls, fuelix.calls.size)
    }

    @Test
    fun aTurnIsDroppedWithoutAskingFuelixAfterATypedMessageOrOnceItsConversationIsGone() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val chat = repositories.chat
        val id = exchange(voice, conversationId = null).conversationId

        val overtaken = assertNotNull(voice.saveTranscript(id, heardNext))
        assertEquals(id, chat.send(id, "typed instead"))
        advanceUntilIdle()
        val calls = fuelix.calls.size
        assertEquals(VoiceAnswer.Dropped, voice.answer(overtaken))
        assertEquals(
            VoiceAnswer.Dropped,
            voice.answer(overtaken.copy(transcriptId = database.messageRows.value.last().id)),
        )

        val orphan = assertNotNull(voice.saveTranscript(id, heardNext))
        assertTrue(chat.delete(id))
        assertEquals(VoiceAnswer.Dropped, voice.answer(orphan))
        assertTrue(database.messageRows.value.isEmpty())
        assertEquals(calls, fuelix.calls.size)
    }

    @Test
    fun aTranscriptWhileATypedReplyIsStillOnItsWaySavesNothing() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val id = assertNotNull(repositories.chat.send(null, "typed first"))

        assertNull(voice.saveTranscript(id, heard))
        advanceTimeBy(PIECE_DELAY * 1.5)
        assertEquals(MessageStatus.STREAMING, database.messageRows.value.last().status)
        assertNull(voice.saveTranscript(id, heard))
        assertEquals(listOf("typed first"), userTexts(id))

        advanceUntilIdle()
        assertEquals(VoiceExchange(transcript = heard), assertNotNull(voice.saveTranscript(id, heard)).exchange)
    }

    @Test
    fun aReplyAnEarlierRunLeftThinkingDoesNotHoldUpTheFirstTranscript() = runTest {
        database = FakeChatDatabase(
            conversations = listOf(
                ConversationEntity(id = 1, title = "left open", modelId = gpt5.id, createdAt = 0, updatedAt = 0),
            ),
            messages = listOf(
                message(id = 1, role = MessageRole.USER, status = MessageStatus.DONE),
                message(id = 2, role = MessageRole.ASSISTANT, status = MessageStatus.THINKING),
            ),
        )
        val voice = repositories().voice

        val turn = assertNotNull(voice.saveTranscript(1, heardNext))

        assertEquals(VoiceExchange(transcript = heardNext), turn.exchange)
        assertEquals(MessageStatus.INTERRUPTED, database.message(2).status)
    }

    @Test
    fun aConversationThatIsNotThereHasNoSessionAndSavesNothing() = runTest {
        val repositories = repositories()
        val voice = repositories.voice

        assertNull(voice.observeSession(42).first())
        assertNull(voice.saveTranscript(42, heard))
        val nowhere = VoiceTurn(conversationId = 42, transcriptId = 1, exchange = VoiceExchange(transcript = heard))
        assertEquals(VoiceAnswer.Dropped, voice.answer(nowhere))
        assertTrue(database.conversationRows.value.isEmpty())
        assertTrue(fuelix.calls.isEmpty())

        val id = exchange(voice, conversationId = null).conversationId
        assertTrue(repositories.chat.delete(id))

        assertNull(voice.observeSession(id).first())
        assertNull(voice.saveTranscript(id, heardNext))
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
        assertEquals(heard, assertIs<Message.User>(thread[0]).text)
        assertEquals(ReplyStatus.DONE, assertIs<Message.Assistant>(thread[1]).status)

        assertEquals(id, chat.send(id, "and in text?"))
        advanceUntilIdle()

        val typed = assertIs<Message.Assistant>(chat.observeMessages(id).first().last())
        assertEquals(listOf(Block.Paragraph(answer(sonnet))), typed.blocks)
        assertEquals(
            listOf(asUser(heard), FuelixMessage(FuelixRole.ASSISTANT, answer(sonnet)), asUser("and in text?")),
            fuelix.calls.last().messages,
        )
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
        assertEquals(gemini.id, fuelix.calls.last().model)
        val typed = assertNotNull(repositories.chat.send(null, "a new chat"))
        assertEquals(gemini, repositories.chat.conversation(typed).model)

        assertTrue(voice.choose(null, ModelChoice(terse)))

        assertEquals(terse.model, repositories.models.observeDefaultModel().first())
        assertEquals(VoiceSession(conversationId = null, model = gpt5), voice.observeSession(null).first())
        val withPersona = assertNotNull(voice.saveTranscript(null, heard, newSession = ModelChoice(terse)))
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
    fun aChoiceWithABlankModelNotStoredOrForAConversationThatIsNotThereChangesNothing() = runTest {
        val repositories = repositories()
        val voice = repositories.voice
        val id = exchange(voice, conversationId = null).conversationId
        val blank = ModelChoice(gemini.copy(id = " "))
        val ghost = ModelChoice(terse.copy(id = "ghost"))

        assertFalse(voice.choose(null, blank))
        assertFalse(voice.choose(null, ghost))
        assertFalse(voice.choose(id, blank))
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
        val calls = fuelix.calls.size

        val session = assertNotNull(repositories.voice.observeSession(id).first())
        exchange(repositories.voice, conversationId = id)

        assertEquals(ModelChoice(sonnet), session.choice)
        assertNull(session.choice.comparing)
        val reply = assertIs<Message.Assistant>(chat.observeMessages(id).first().last())
        assertEquals(sonnet, reply.model)
        assertEquals(gpt5, chat.conversation(id).comparing)
        assertEquals(listOf(sonnet.id), fuelix.calls.drop(calls).map { it.model })
    }

    @Test
    fun aTranscriptAndAReplyAreEachWrittenInOneTransaction() = runTest {
        val repositories = repositories()

        val id = exchange(repositories.voice, conversationId = null).conversationId
        exchange(repositories.voice, conversationId = id, transcript = heardNext)

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
        modelId = if (role == MessageRole.USER) null else gpt5.id,
        status = status,
        latencyMillis = null,
        createdAt = 0,
    )
}
