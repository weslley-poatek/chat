package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
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
import br.com.weslleycampos.chat.data.chat.models.ReplyFailed
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import br.com.weslleycampos.chat.data.fuelix.FuelixException
import br.com.weslleycampos.chat.data.fuelix.FuelixMessage
import br.com.weslleycampos.chat.data.fuelix.FuelixRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomChatRepositoryTest {
    private var database = FakeChatDatabase()
    private val preferences = FakePreferences()
    private val fuelix = FakeFuelixClient()

    private val reviewer: Persona = PersonaPresets.personas.single { it.id == "reviewer" }
    private val terse: Persona = PersonaPresets.personas.single { it.id == "terse" }
    private val digest: Persona = PersonaPresets.personas.single { it.id == "digest" }
    private val sonnet: ChatModel = ModelCatalogue.default
    private val gpt5: ChatModel = ModelCatalogue["gpt-5"]
    private val gemini: ChatModel = ModelCatalogue["gemini-2.5-pro"]

    /** The personas and preferences the chat repository reads, on the same fake database. */
    private fun TestScope.models(): ModelRepository = LocalModelRepository(
        personaDao = database.personaDao,
        preferences = preferences,
        fuelix = fuelix,
        scope = this,
    )

    /**
     * Replies stream from [fuelix] on the test's virtual time, in the test's own scope by default,
     * since `advanceUntilIdle` skips `backgroundScope`.
     */
    private fun TestScope.repository(
        scope: CoroutineScope = this,
        models: ModelRepository = models(),
    ): RoomChatRepository {
        val clock = TestClock(testScheduler)
        return RoomChatRepository(
            conversationDao = database.conversationDao,
            historyDao = database.historyDao,
            messageDao = database.messageDao,
            compareDao = database.compareDao,
            transactions = database.transactions,
            models = models,
            streamer = ReplyStreamer(messageDao = database.messageDao, fuelix = fuelix, clock = clock),
            clock = clock,
            scope = scope,
        )
    }

    private suspend fun ChatRepository.replies(conversationId: Long): List<Message.Assistant> =
        observeMessages(conversationId).first().filterIsInstance<Message.Assistant>()

    /** Every failure [repository] tells from now on. */
    private fun TestScope.failuresOf(repository: ChatRepository): List<ReplyFailed> {
        val failures = mutableListOf<ReplyFailed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.replyFailures.collect { failures += it }
        }
        return failures
    }

    /** What [model] answers when nothing else is scripted for it. */
    private fun answer(model: ChatModel): List<Block> = listOf(Block.Paragraph(FakeFuelixClient.answer(model.id)))

    private fun asUser(text: String) = FuelixMessage(FuelixRole.USER, text)

    private fun asModel(text: String) = FuelixMessage(FuelixRole.ASSISTANT, text)

    @Test
    fun aFirstSendCreatesAConversationTitledAfterTheMessageOnTheDefaultModel() = runTest {
        val repository = repository()

        val id = assertNotNull(repository.send(null, "  Write a useDebounce hook in TS. Generic, cancel on unmount.\n"))

        assertEquals(
            Conversation(
                id = id,
                title = "Write a useDebounce hook in TS. Generic,",
                model = ModelCatalogue.default,
                createdAt = TEST_START,
                updatedAt = TEST_START,
            ),
            repository.observeConversation(id).first(),
        )
    }

    @Test
    fun sendSavesTheTrimmedMessageAndAThinkingReplyBeforeItReturns() = runTest {
        val repository = repository()

        val id = assertNotNull(repository.send(null, "  Add a leading option  "))

        val messages = repository.observeMessages(id).first()
        assertEquals(2, messages.size)
        val (user, reply) = messages
        assertEquals(
            Message.User(id = user.id, conversationId = id, text = "Add a leading option", createdAt = TEST_START),
            user,
        )
        assertEquals(
            Message.Assistant(
                id = reply.id,
                conversationId = id,
                model = ModelCatalogue.default,
                blocks = emptyList(),
                status = ReplyStatus.THINKING,
                latency = null,
                createdAt = TEST_START,
            ),
            reply,
        )
    }

    @Test
    fun aReplyGoesFromThinkingThroughStreamingToDoneAndEveryWriteReachesObservers() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "why is my debounce stale?"))
        val replies = mutableListOf<Message.Assistant>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeMessages(id).collect { replies += it.last() as Message.Assistant }
        }

        advanceUntilIdle()

        // Four pieces 50 ms apart, each written as it arrives, then the whole reply written done.
        val pieces = FakeFuelixClient.DEFAULT_PIECES
        assertEquals(pieces + 1, database.replyUpdates.size)
        assertEquals(
            listOf(ReplyStatus.THINKING) + List(pieces) { ReplyStatus.STREAMING } + ReplyStatus.DONE,
            replies.map { it.status },
        )
        assertEquals(listOf(Block.Paragraph("Answer")), replies[1].blocks)
        assertEquals(answer(sonnet), replies.last().blocks)
        assertEquals(PIECE_DELAY * (pieces - 1), replies.last().latency)
        assertEquals((PIECE_DELAY * pieces).inWholeMilliseconds, currentTime)
    }

    @Test
    fun eachSendAsksTheConversationsModelWithTheConversationSoFarAndANewConversationStartsOver() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "Write a useDebounce hook"))
        advanceUntilIdle()
        fuelix.reply(sonnet.id, "Add a ", "`leading` flag.")
        repository.send(id, "Add a leading option")
        advanceUntilIdle()
        repository.send(id, "Now cancel on unmount")
        advanceUntilIdle()
        val other = assertNotNull(repository.send(null, "Postgres index"))
        advanceUntilIdle()

        assertEquals(List(4) { sonnet.id }, fuelix.calls.map { it.model })
        assertEquals(listOf(asUser("Write a useDebounce hook")), fuelix.calls[0].messages)
        assertEquals(
            listOf(
                asUser("Write a useDebounce hook"),
                asModel(FakeFuelixClient.answer(sonnet.id)),
                asUser("Add a leading option"),
                asModel("Add a `leading` flag."),
                asUser("Now cancel on unmount"),
            ),
            fuelix.calls[2].messages,
        )
        assertEquals(listOf(asUser("Postgres index")), fuelix.calls[3].messages)
        assertEquals(
            listOf(answer(sonnet), listOf(Block.Paragraph("Add a `leading` flag.")), answer(sonnet)),
            repository.replies(id).map { it.blocks },
        )
        assertEquals(listOf(answer(sonnet)), repository.replies(other).map { it.blocks })
    }

    @Test
    fun aSendWhileTheLastReplyThinksOrStreamsSendsNothing() = runTest {
        val repository = repository()
        fuelix.hold(afterPieces = 0)
        val id = assertNotNull(repository.send(null, "first"))
        advanceUntilIdle()

        assertEquals(ReplyStatus.THINKING, repository.replies(id).single().status)
        assertNull(repository.send(id, "while thinking"))
        fuelix.hold(afterPieces = 1)
        advanceUntilIdle()
        assertEquals(ReplyStatus.STREAMING, repository.replies(id).single().status)
        assertNull(repository.send(id, "while streaming"))
        assertEquals(2, repository.observeMessages(id).first().size)

        fuelix.release()
        advanceUntilIdle()
        assertEquals(1, fuelix.calls.size)
        assertEquals(id, repository.send(id, "after it is done"))
        assertEquals(4, repository.observeMessages(id).first().size)
    }

    @Test
    fun blankTextOrAConversationThatDoesNotExistSendsNothing() = runTest {
        val repository = repository()

        assertNull(repository.send(null, "  \n\t "))
        assertNull(repository.send(42, "hello"))
        advanceUntilIdle()

        assertTrue(database.conversationRows.value.isEmpty())
        assertTrue(database.messageRows.value.isEmpty())
        assertNull(repository.observeConversation(42).first())
        assertTrue(repository.observeMessages(42).first().isEmpty())
        assertTrue(fuelix.calls.isEmpty())
    }

    @Test
    fun recentConversationsAreTheThreeLastUpdatedNewestFirstAndASendMovesOneUp() = runTest {
        val repository = repository()
        val ids = listOf("first", "second", "third", "fourth").map { text ->
            assertNotNull(repository.send(null, text)).also { advanceUntilIdle() }
        }
        assertEquals(ids.reversed().take(3), repository.observeRecentConversations().first().map { it.id })

        repository.send(ids[0], "back to the first")

        val recent = repository.observeRecentConversations().first()
        assertEquals(listOf(ids[0], ids[3], ids[2]), recent.map { it.id })
        assertEquals(TEST_START + currentTime.milliseconds, recent.first().updatedAt)
        assertEquals(TEST_START, recent.first().createdAt)
        assertEquals("first", recent.first().title)
    }

    @Test
    fun aReplyStillPlayingWhenTheAppWasClosedIsInterruptedWhenTheRepositoryStarts() = runTest {
        val partial = listOf(StoredBlock.Paragraph("Two li"))
        database = FakeChatDatabase(
            conversations = listOf(
                ConversationEntity(id = 1, title = "t", modelId = gpt5.id, createdAt = 0, updatedAt = 0),
            ),
            messages = listOf(
                message(id = 1, role = MessageRole.USER, status = MessageStatus.DONE),
                message(id = 2, role = MessageRole.ASSISTANT, status = MessageStatus.STREAMING, blocks = partial),
            ),
        )
        val repository = repository()

        val interrupted = repository.replies(1).single()

        assertEquals(ReplyStatus.INTERRUPTED, interrupted.status)
        assertEquals(listOf(Block.Paragraph("Two li")), interrupted.blocks)
        assertEquals(gpt5, interrupted.model)
        assertEquals(1L, repository.send(1, "try again"))
        advanceUntilIdle()
        assertEquals(answer(gpt5), repository.replies(1).last().blocks)
        assertEquals(gpt5, repository.replies(1).last().model)
        assertEquals(
            listOf(asUser("why?"), asModel("Two li"), asUser("try again")),
            fuelix.calls.single().messages,
        )
    }

    @Test
    fun aConversationStoredOnAnIdFromBeforeFuelixIsAnsweredByTheModelItBecame() = runTest {
        database = FakeChatDatabase(
            conversations = listOf(
                ConversationEntity(id = 1, title = "t", modelId = "gpt5", createdAt = 0, updatedAt = 0),
            ),
            messages = listOf(message(id = 1, role = MessageRole.USER, status = MessageStatus.DONE)),
        )
        val repository = repository()

        assertEquals(1L, repository.send(1, "still there?"))
        advanceUntilIdle()

        assertEquals(gpt5.id, fuelix.calls.single().model)
        assertEquals(gpt5, repository.replies(1).single().model)
        assertEquals(answer(gpt5), repository.replies(1).single().blocks)
    }

    @Test
    fun aReplyKeepsStreamingIntoItsConversationWhileANewChatSends() = runTest {
        val repository = repository()
        val first = assertNotNull(repository.send(null, "first"))
        advanceTimeBy(PIECE_DELAY * 1.5)
        assertEquals(ReplyStatus.STREAMING, repository.replies(first).single().status)

        val second = assertNotNull(repository.send(null, "a new chat"))
        advanceUntilIdle()

        assertNotEquals(first, second)
        assertEquals(listOf(ReplyStatus.DONE), repository.replies(first).map { it.status })
        assertEquals(listOf(ReplyStatus.DONE), repository.replies(second).map { it.status })
        assertEquals(answer(sonnet), repository.replies(first).single().blocks)
        assertEquals(answer(sonnet), repository.replies(second).single().blocks)
    }

    @Test
    fun aConversationsObserversStayQuietWhileAnotherConversationStreams() = runTest {
        val repository = repository()
        val quiet = assertNotNull(repository.send(null, "first"))
        advanceUntilIdle()
        var emissions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeMessages(quiet).collect { emissions++ }
        }
        var conversationEmissions = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeConversation(quiet).collect { conversationEmissions++ }
        }

        repository.send(null, "another conversation")
        advanceUntilIdle()

        assertEquals(1, emissions)
        assertEquals(1, conversationEmissions)
    }

    @Test
    fun aSendWritesItsConversationAndBothMessagesInOneTransaction() = runTest {
        val repository = repository()

        val id = assertNotNull(repository.send(null, "first"))
        assertEquals(3, database.writes)
        assertEquals(3, database.writesInTransactions)
        advanceUntilIdle()

        val before = database.writes
        repository.send(id, "second")
        assertEquals(before + 3, database.writes)
        assertEquals(6, database.writesInTransactions)
    }

    @Test
    fun aReplyWhoseStreamIsCancelledIsLeftInterruptedWithWhatItShowedAndNoFailureIsTold() = runTest {
        val streams = CoroutineScope(coroutineContext + Job(coroutineContext.job))
        val repository = repository(scope = streams)
        val failures = failuresOf(repository)
        val id = assertNotNull(repository.send(null, "first"))
        advanceTimeBy(PIECE_DELAY * 1.5)

        streams.cancel()
        runCurrent()

        val reply = repository.replies(id).single()
        assertEquals(ReplyStatus.INTERRUPTED, reply.status)
        assertEquals(listOf(Block.Paragraph("Answer")), reply.blocks)
        assertNull(reply.latency)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun aReplyThatFailsIsLeftInterruptedWithWhatItShowedAndItsFailureIsToldWithItsConversation() = runTest {
        val repository = repository()
        val failures = failuresOf(repository)
        fuelix.reply(sonnet.id, "Two ", "lines", failure = FuelixException.RateLimited())

        val id = assertNotNull(repository.send(null, "first"))
        advanceUntilIdle()

        assertEquals(listOf(ReplyFailed(conversationId = id, failure = ReplyFailure.RATE_LIMITED)), failures)
        val reply = repository.replies(id).single()
        assertEquals(ReplyStatus.INTERRUPTED, reply.status)
        assertEquals(listOf(Block.Paragraph("Two lines")), reply.blocks)
        assertNull(reply.latency)
        assertEquals(id, repository.send(id, "try again"))
        advanceUntilIdle()
        assertEquals(ReplyStatus.DONE, repository.replies(id).last().status)
        assertEquals(1, failures.size)
    }

    @Test
    fun anEmptyReplyAndAMissingKeyAreToldAsFailuresOfTheirOwnConversations() = runTest {
        val repository = repository()
        val failures = failuresOf(repository)
        fuelix.reply(sonnet.id)
        fuelix.reply(sonnet.id, failure = FuelixException.MissingKey())

        val empty = assertNotNull(repository.send(null, "first"))
        advanceUntilIdle()
        val noKey = assertNotNull(repository.send(null, "second"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                ReplyFailed(conversationId = empty, failure = ReplyFailure.FAILED),
                ReplyFailed(conversationId = noKey, failure = ReplyFailure.NO_KEY),
            ),
            failures,
        )
        assertEquals(
            listOf(ReplyStatus.INTERRUPTED, ReplyStatus.INTERRUPTED),
            listOf(empty, noKey).map { repository.replies(it).single().status },
        )
    }

    @Test
    fun aFailureNobodyIsCollectingIsNotToldLater() = runTest {
        val repository = repository()
        fuelix.reply(sonnet.id, failure = FuelixException.KeyRefused())
        fuelix.reply(sonnet.id, failure = FuelixException.Unreachable(cause = IllegalStateException("offline")))
        assertNotNull(repository.send(null, "first"))
        advanceUntilIdle()

        val failures = failuresOf(repository)
        val second = assertNotNull(repository.send(null, "second"))
        advanceUntilIdle()

        assertEquals(listOf(ReplyFailed(conversationId = second, failure = ReplyFailure.UNREACHABLE)), failures)
    }

    @Test
    fun aFirstSendWithNoChoiceStartsOnTheDefaultModelTheUserSet() = runTest {
        val models = models()
        models.setDefaultModel(ModelCatalogue["o3"])
        val repository = repository(models = models)

        val id = assertNotNull(repository.send(null, "Rust ownership in async closures"))
        advanceUntilIdle()

        val conversation = assertNotNull(repository.observeConversation(id).first())
        assertEquals(ModelChoice(ModelCatalogue["o3"]), conversation.choice)
        assertEquals(ModelCatalogue["o3"], repository.replies(id).single().model)
        assertNull(repository.replies(id).single().persona)
        assertEquals("o3", fuelix.calls.single().model)
    }

    @Test
    fun aFirstSendStartsOnTheModelPickedBeforeItAndLeavesTheDefaultAlone() = runTest {
        val models = models()
        val repository = repository(models = models)

        val id = assertNotNull(repository.send(null, "SSE vs WebSocket", newChat = ModelChoice(gemini)))

        assertEquals(ModelChoice(gemini), repository.observeConversation(id).first()?.choice)
        assertEquals(gemini, repository.replies(id).single().model)
        assertEquals(ModelCatalogue.default, models.observeDefaultModel().first())
        assertEquals(
            ConversationEntityChoice(gemini.id, null),
            database.conversationRows.value.single().choice(),
        )
    }

    @Test
    fun aFirstSendWithAPersonaStartsOnThePersonaAndAsksItsModelWithItsPrompt() = runTest {
        val repository = repository()

        val choice = ModelChoice(model = gemini, persona = terse)

        val id = assertNotNull(repository.send(null, "Review this diff", newChat = choice))
        advanceUntilIdle()

        val conversation = assertNotNull(repository.observeConversation(id).first())
        assertEquals(terse, conversation.persona)
        assertEquals(gpt5, conversation.model)
        val reply = repository.replies(id).single()
        assertEquals(terse, reply.persona)
        assertEquals(gpt5, reply.model)
        assertEquals(ReplyStatus.DONE, reply.status)
        val call = fuelix.calls.single()
        assertEquals(gpt5.id, call.model)
        assertEquals(
            listOf(FuelixMessage(FuelixRole.SYSTEM, terse.prompt), asUser("Review this diff")),
            call.messages,
        )
    }

    @Test
    fun aNewChatChoiceWithABlankModelOrAPersonaThatIsNotStoredSendsNothing() = runTest {
        val repository = repository()
        val blank = gemini.copy(id = " ")
        val ghost = terse.copy(id = "ghost")

        assertNull(repository.send(null, "hello", newChat = ModelChoice(blank)))
        assertNull(repository.send(null, "hello", newChat = ModelChoice(ghost)))

        assertTrue(database.conversationRows.value.isEmpty())
        assertTrue(database.messageRows.value.isEmpty())
    }

    @Test
    fun aNewChatCanStartOnAnyModelTheKeyLists() = runTest {
        val repository = repository()
        val haiku = ModelCatalogue.of("claude-haiku-4-5-20251001")

        val id = assertNotNull(repository.send(null, "hello", newChat = ModelChoice(haiku)))
        advanceUntilIdle()

        assertEquals(haiku, repository.observeConversation(id).first()?.model)
        assertEquals(haiku, repository.replies(id).single().model)
        assertEquals(haiku.id, fuelix.calls.single().model)
    }

    @Test
    fun anOpenConversationAnswersWithItsOwnChoiceAndIgnoresANewChatOne() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "first", newChat = ModelChoice(reviewer)))
        advanceUntilIdle()

        assertEquals(id, repository.send(id, "second", newChat = ModelChoice(gemini)))
        advanceUntilIdle()

        val replies = repository.replies(id)
        assertEquals(listOf(reviewer, reviewer), replies.map { it.persona })
        assertEquals(listOf(sonnet, sonnet), replies.map { it.model })
        assertEquals(ModelChoice(reviewer), repository.observeConversation(id).first()?.choice)
        assertEquals(listOf(sonnet.id, sonnet.id), fuelix.calls.map { it.model })
    }

    @Test
    fun choosingAModelChangesTheConversationDropsItsPersonaAndKeepsItsPlaceInRecent() = runTest {
        val repository = repository()
        val older = assertNotNull(repository.send(null, "older", newChat = ModelChoice(reviewer)))
        advanceUntilIdle()
        val newer = assertNotNull(repository.send(null, "newer"))
        advanceUntilIdle()
        val before = assertNotNull(repository.observeConversation(older).first())

        assertTrue(repository.choose(older, ModelChoice(gemini)))

        val after = assertNotNull(repository.observeConversation(older).first())
        assertEquals(before.copy(model = gemini, persona = null), after)
        assertEquals(listOf(newer, older), repository.observeRecentConversations().first().map { it.id })
    }

    @Test
    fun choosingAPersonaPicksTheModelItAnswersWith() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "Summarise this paper"))
        advanceUntilIdle()

        val opus = ModelCatalogue["claude-opus-4-1-20250805"]

        assertTrue(repository.choose(id, ModelChoice(model = opus, persona = digest)))

        assertEquals(ModelChoice(digest), repository.observeConversation(id).first()?.choice)
        assertEquals(
            ConversationEntityChoice(gemini.id, "digest"),
            database.conversationRows.value.single().choice(),
        )
    }

    @Test
    fun theNextReplyComesFromTheNewChoiceWhileTheReplyOnItsWayAndEarlierOnesKeepTheirs() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "first", newChat = ModelChoice(terse)))
        advanceTimeBy(PIECE_DELAY * 1.5)
        assertEquals(ReplyStatus.STREAMING, repository.replies(id).single().status)

        assertTrue(repository.choose(id, ModelChoice(gemini)))
        advanceUntilIdle()
        repository.send(id, "second")
        assertTrue(repository.choose(id, ModelChoice(reviewer)))
        advanceUntilIdle()
        repository.send(id, "third")
        advanceUntilIdle()

        val replies = repository.replies(id)
        assertEquals(listOf(gpt5, gemini, sonnet), replies.map { it.model })
        assertEquals(listOf(terse, null, reviewer), replies.map { it.persona })
        assertEquals(List(3) { ReplyStatus.DONE }, replies.map { it.status })
        assertEquals(listOf(gpt5.id, gemini.id, sonnet.id), fuelix.calls.map { it.model })
        assertEquals(
            listOf(terse.prompt, null, reviewer.prompt),
            fuelix.calls.map { call -> call.messages.firstOrNull { it.role == FuelixRole.SYSTEM }?.content },
        )
    }

    @Test
    fun choosingForAConversationThatDoesNotExistOrAChoiceThatIsNotStoredChangesNothing() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "first"))
        val writes = database.writes

        assertFalse(repository.choose(id + 1, ModelChoice(gemini)))
        assertFalse(repository.choose(id, ModelChoice(gemini.copy(id = ""))))
        assertFalse(repository.choose(id, ModelChoice(terse.copy(id = "ghost"))))

        assertEquals(writes, database.writes)
        assertEquals(ModelChoice(ModelCatalogue.default), repository.observeConversation(id).first()?.choice)
    }

    @Test
    fun aConversationsObserversSeeAChoiceAsSoonAsItIsMade() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "first"))
        advanceUntilIdle()
        val choices = mutableListOf<ModelChoice?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeConversation(id).collect { choices += it?.choice }
        }

        repository.choose(id, ModelChoice(terse))
        repository.choose(id, ModelChoice(terse))
        repository.choose(id, ModelChoice(gemini))

        assertEquals(
            listOf<ModelChoice?>(ModelChoice(ModelCatalogue.default), ModelChoice(terse), ModelChoice(gemini)),
            choices,
        )
    }

    @Test
    fun aPersonaThatIsNoLongerStoredReadsAsNoneAndItsPromptIsNotSent() = runTest {
        database = FakeChatDatabase(
            conversations = listOf(
                ConversationEntity(
                    id = 1,
                    title = "t",
                    modelId = gpt5.id,
                    createdAt = 0,
                    updatedAt = 0,
                    personaId = "ghost",
                ),
            ),
            messages = listOf(
                message(id = 1, role = MessageRole.USER, status = MessageStatus.DONE),
                message(id = 2, role = MessageRole.ASSISTANT, status = MessageStatus.DONE).copy(personaId = "ghost"),
            ),
        )
        val repository = repository()

        assertEquals(ModelChoice(gpt5), repository.observeConversation(1).first()?.choice)
        assertNull(repository.replies(1).single().persona)
        assertEquals(1L, repository.send(1, "again"))
        advanceUntilIdle()
        assertTrue(fuelix.calls.single().messages.none { it.role == FuelixRole.SYSTEM })
    }

    private fun message(
        id: Long,
        role: MessageRole,
        status: MessageStatus,
        blocks: List<StoredBlock> = emptyList(),
    ) = MessageEntity(
        id = id,
        conversationId = 1,
        role = role,
        text = if (role == MessageRole.USER) "why?" else "",
        blocks = blocks,
        modelId = if (role == MessageRole.USER) null else gpt5.id,
        status = status,
        latencyMillis = null,
        createdAt = 0,
    )

    private data class ConversationEntityChoice(val modelId: String, val personaId: String?)

    private fun ConversationEntity.choice() = ConversationEntityChoice(modelId, personaId)
}
