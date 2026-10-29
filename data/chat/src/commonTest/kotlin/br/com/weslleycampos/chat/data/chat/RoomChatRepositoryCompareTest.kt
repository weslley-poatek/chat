package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakeFuelixClient
import br.com.weslleycampos.chat.data.chat.fakes.FakeFuelixClient.Companion.PIECE_DELAY
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
import br.com.weslleycampos.chat.data.chat.fakes.TEST_START
import br.com.weslleycampos.chat.data.chat.fakes.TestClock
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.CompareSide
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Compare mode in [RoomChatRepository]: the stored compare choice, and the pairs a send answers with. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomChatRepositoryCompareTest {
    private var database = FakeChatDatabase()
    private val fuelix = FakeFuelixClient()

    private val sonnet = ModelCatalogue.default
    private val gpt5 = ModelCatalogue["gpt-5"]
    private val gemini = ModelCatalogue["gemini-2.5-pro"]
    private val terse: Persona = PersonaPresets.personas.single { it.id == "terse" }

    /** Sonnet compared with GPT-5, the prototype's "sonnet-4.5 ⇄ gpt-5". */
    private val comparing = ModelChoice(model = sonnet, compareOn = true, compareWith = gpt5)

    /** Replies stream from [fuelix] on the test's virtual time, in the test's own scope by default. */
    private fun TestScope.repository(scope: CoroutineScope = this): RoomChatRepository {
        val clock = TestClock(testScheduler)
        return RoomChatRepository(
            conversationDao = database.conversationDao,
            historyDao = database.historyDao,
            messageDao = database.messageDao,
            compareDao = database.compareDao,
            transactions = database.transactions,
            models = LocalModelRepository(
                personaDao = database.personaDao,
                preferences = FakePreferences(),
                fuelix = fuelix,
                scope = this,
            ),
            streamer = ReplyStreamer(messageDao = database.messageDao, fuelix = fuelix, clock = clock),
            clock = clock,
            scope = scope,
        )
    }

    private suspend fun ChatRepository.thread(conversationId: Long): List<Message> =
        observeMessages(conversationId).first()

    private suspend fun ChatRepository.pair(conversationId: Long): Message.ComparePair =
        assertIs<Message.ComparePair>(thread(conversationId).last())

    private suspend fun ChatRepository.conversation(id: Long): Conversation =
        assertNotNull(observeConversation(id).first())

    private fun Message.ComparePair.statuses(): List<ReplyStatus> = sides.map { it.status }

    private fun row(id: Long): ConversationEntity = database.conversationRows.value.single { it.id == id }

    /** What [model] answers when nothing else is scripted for it. */
    private fun answer(model: ChatModel): List<Block> = listOf(Block.Paragraph(FakeFuelixClient.answer(model.id)))

    private fun asUser(text: String) = FuelixMessage(FuelixRole.USER, text)

    private fun asModel(model: ChatModel) = FuelixMessage(FuelixRole.ASSISTANT, FakeFuelixClient.answer(model.id))

    @Test
    fun aFirstSendWhileComparingCreatesAComparingConversationAnsweredByAThinkingPair() = runTest {
        val repository = repository()

        val id = assertNotNull(repository.send(null, "SSE or WebSocket for a dashboard?", newChat = comparing))

        val conversation = repository.conversation(id)
        assertEquals(comparing, conversation.choice)
        assertEquals(gpt5, conversation.comparing)
        val (question, reply) = repository.thread(id)
        assertEquals("SSE or WebSocket for a dashboard?", assertIs<Message.User>(question).text)
        val pair = assertIs<Message.ComparePair>(reply)
        val thinking = CompareSide(
            id = pair.id,
            model = sonnet,
            blocks = emptyList(),
            status = ReplyStatus.THINKING,
            latency = null,
        )
        assertEquals(listOf(thinking, thinking.copy(id = pair.id + 1, model = gpt5)), pair.sides)
        assertEquals(TEST_START, pair.createdAt)
        assertTrue(pair.isInProgress)
        val sides = database.messageRows.value.filter { it.role == MessageRole.ASSISTANT }
        assertEquals(listOf(PAIR_FIRST_SIDE, PAIR_SECOND_SIDE), sides.map { it.pairSide })
        assertEquals(listOf(null, null), sides.map { it.personaId })
        assertEquals(true to gpt5.id, row(id).let { it.compareOn to it.compareModelId })
    }

    @Test
    fun aPairsSidesStreamAtOnceEachFromItsOwnModelEachDoneWithItsOwnLatency() = runTest {
        val repository = repository()
        fuelix.reply(gpt5.id, "Most ", "likely ", "SSE.")
        val id = assertNotNull(repository.send(null, "why is my debounce stale?", newChat = comparing))
        val pairs = mutableListOf<Message.ComparePair>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeMessages(id).collect { pairs += it.last() as Message.ComparePair }
        }

        advanceUntilIdle()

        val pair = pairs.last()
        assertEquals(listOf(answer(sonnet), listOf(Block.Paragraph("Most likely SSE."))), pair.sides.map { it.blocks })
        // Sonnet's four pieces and GPT-5's three, 50 ms apart, side by side.
        assertEquals(listOf(PIECE_DELAY * 3, PIECE_DELAY * 2), pair.sides.map { it.latency })
        assertEquals((PIECE_DELAY * FakeFuelixClient.DEFAULT_PIECES).inWholeMilliseconds, currentTime)
        assertEquals(listOf(ReplyStatus.THINKING, ReplyStatus.THINKING), pairs.first().statuses())
        assertTrue(pairs.any { it.statuses() == listOf(ReplyStatus.STREAMING, ReplyStatus.DONE) })
        assertEquals(listOf(ReplyStatus.DONE, ReplyStatus.DONE), pair.statuses())
        assertFalse(pair.isInProgress)
        assertEquals(listOf(sonnet.id, gpt5.id), fuelix.calls.map { it.model })
        assertEquals(List(2) { listOf(asUser("why is my debounce stale?")) }, fuelix.calls.map { it.messages })
    }

    @Test
    fun aSendWaitsForBothSidesOfThePair() = runTest {
        val repository = repository()
        fuelix.hold(afterPieces = 1, model = sonnet.id)
        val id = assertNotNull(repository.send(null, "first", newChat = comparing))

        advanceUntilIdle()
        assertEquals(listOf(ReplyStatus.STREAMING, ReplyStatus.DONE), repository.pair(id).statuses())
        assertNull(repository.send(id, "while the first side streams"))
        assertEquals(2, repository.thread(id).size)

        fuelix.release()
        advanceUntilIdle()
        assertEquals(id, repository.send(id, "after both are done"))
        assertEquals(4, repository.thread(id).size)
    }

    @Test
    fun eachSideOfTheNextTurnReadsItsOwnSideOfEarlierPairsAndASingleReplyTheFirstSide() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "one"))
        advanceUntilIdle()
        assertTrue(repository.choose(id, comparing))
        repository.send(id, "two")
        advanceUntilIdle()
        repository.send(id, "three")
        advanceUntilIdle()
        assertTrue(repository.choose(id, comparing.toggledCompare()))
        repository.send(id, "four")
        advanceUntilIdle()

        assertEquals(listOf(sonnet, sonnet, gpt5, sonnet, gpt5, sonnet).map { it.id }, fuelix.calls.map { it.model })
        val (sonnetThree, gptThree) = fuelix.calls.subList(3, 5).map { it.messages }
        assertEquals(
            listOf(asUser("one"), asModel(sonnet), asUser("two"), asModel(gpt5), asUser("three")),
            gptThree,
        )
        assertEquals(
            listOf(asUser("one"), asModel(sonnet), asUser("two"), asModel(sonnet), asUser("three")),
            sonnetThree,
        )
        val replies = repository.thread(id).filterNot { it is Message.User }
        assertEquals(answer(sonnet), assertIs<Message.Assistant>(replies[0]).blocks)
        replies.subList(1, 3).forEach { pair ->
            val sides = assertIs<Message.ComparePair>(pair).sides
            assertEquals(listOf(answer(sonnet), answer(gpt5)), sides.map { it.blocks })
        }
        assertEquals(sonnet, assertIs<Message.Assistant>(replies[3]).model)
    }

    @Test
    fun aConversationStillComparesAfterARestartAndItsNextSendIsAPairAgain() = runTest {
        val id = assertNotNull(repository().send(null, "first", newChat = comparing))
        advanceUntilIdle()

        val restarted = repository()

        assertEquals(comparing, restarted.conversation(id).choice)
        assertEquals(id, restarted.send(id, "second"))
        advanceUntilIdle()
        val pair = restarted.pair(id)
        assertEquals(listOf(sonnet, gpt5), pair.sides.map { it.model })
        assertEquals(listOf(answer(sonnet), answer(gpt5)), pair.sides.map { it.blocks })
    }

    @Test
    fun aPairStillPlayingWhenTheAppWasClosedHasBothSidesInterruptedWhenTheRepositoryStarts() = runTest {
        database = FakeChatDatabase(
            conversations = listOf(comparingRow(id = 1), comparingRow(id = 2)),
            messages = listOf(
                stored(id = 1, role = MessageRole.USER, status = MessageStatus.DONE),
                stored(id = 2, pairSide = PAIR_FIRST_SIDE, status = MessageStatus.STREAMING, text = "Two li"),
                stored(id = 3, pairSide = PAIR_SECOND_SIDE, status = MessageStatus.THINKING),
                stored(id = 4, role = MessageRole.USER, status = MessageStatus.DONE, conversationId = 2),
                stored(id = 5, pairSide = PAIR_FIRST_SIDE, status = MessageStatus.DONE, conversationId = 2),
                stored(id = 6, pairSide = PAIR_SECOND_SIDE, status = MessageStatus.STREAMING, conversationId = 2),
            ),
        )
        val repository = repository()

        val interrupted = repository.pair(1)
        assertEquals(listOf(ReplyStatus.INTERRUPTED, ReplyStatus.INTERRUPTED), interrupted.statuses())
        assertEquals(listOf(listOf(Block.Paragraph("Two li")), emptyList()), interrupted.sides.map { it.blocks })
        assertEquals(listOf(ReplyStatus.DONE, ReplyStatus.INTERRUPTED), repository.pair(2).statuses())
        assertEquals(1L, repository.send(1, "try again"))
        advanceUntilIdle()
        assertEquals(listOf(answer(sonnet), answer(gpt5)), repository.pair(1).sides.map { it.blocks })
    }

    @Test
    fun aPairWhoseStreamIsCancelledHasBothSidesInterruptedWithWhatTheyShowed() = runTest {
        val streams = CoroutineScope(coroutineContext + Job(coroutineContext.job))
        val repository = repository(scope = streams)
        val id = assertNotNull(repository.send(null, "first", newChat = comparing))
        advanceTimeBy(PIECE_DELAY * 1.5)

        streams.cancel()
        runCurrent()

        val pair = repository.pair(id)
        assertEquals(listOf(ReplyStatus.INTERRUPTED, ReplyStatus.INTERRUPTED), pair.statuses())
        assertEquals(List(2) { listOf(Block.Paragraph("Answer")) }, pair.sides.map { it.blocks })
        assertEquals(listOf(null, null), pair.sides.map { it.latency })
        assertFalse(pair.isInProgress)
    }

    @Test
    fun aSideThatFailsIsLeftInterruptedWhileTheOtherFinishesAndTheFailureIsToldOnce() = runTest {
        val repository = repository()
        val failures = mutableListOf<ReplyFailed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.replyFailures.collect { failures += it }
        }
        fuelix.reply(gpt5.id, "Most ", failure = FuelixException.ModelUnavailable(status = 404))

        val id = assertNotNull(repository.send(null, "first", newChat = comparing))
        advanceUntilIdle()

        assertEquals(listOf(ReplyFailed(conversationId = id, failure = ReplyFailure.MODEL_UNAVAILABLE)), failures)
        val pair = repository.pair(id)
        assertEquals(listOf(ReplyStatus.DONE, ReplyStatus.INTERRUPTED), pair.statuses())
        assertEquals(listOf(answer(sonnet), listOf(Block.Paragraph("Most"))), pair.sides.map { it.blocks })
        assertEquals(id, repository.send(id, "again"))
    }

    @Test
    fun deletingAComparingConversationWhileItsPairStreamsStopsBothSides() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "first", newChat = comparing))
        advanceTimeBy(PIECE_DELAY * 1.5)

        assertTrue(repository.delete(id))
        advanceUntilIdle()

        // Each side's second piece finds its row gone, and both streams stop there.
        assertEquals(listOf(2, 2), fuelix.calls.map { it.sent })
        assertEquals(4, database.replyUpdates.size)
        assertEquals((PIECE_DELAY * 2).inWholeMilliseconds, currentTime)
        assertTrue(repository.thread(id).isEmpty())
    }

    @Test
    fun aPairSendWritesItsConversationTheMessageAndBothSidesInOneTransaction() = runTest {
        val repository = repository()

        val id = assertNotNull(repository.send(null, "first", newChat = comparing))
        assertEquals(4, database.writes)
        assertEquals(4, database.writesInTransactions)
        advanceUntilIdle()

        val before = database.writes
        repository.send(id, "second")
        assertEquals(before + 4, database.writes)
        assertEquals(8, database.writesInTransactions)
    }

    @Test
    fun theCompareButtonAndASecondModelAreStoredWithTheModelInOneTransactionAndKeepThePlaceInRecent() = runTest {
        val repository = repository()
        val older = assertNotNull(repository.send(null, "older")).also { advanceUntilIdle() }
        val newer = assertNotNull(repository.send(null, "newer")).also { advanceUntilIdle() }
        val before = repository.conversation(older)
        val writes = database.writesInTransactions

        val on = before.choice.toggledCompare()
        assertTrue(repository.choose(older, on))
        assertEquals(before.copy(compareOn = true), repository.conversation(older))
        assertTrue(repository.choose(older, assertNotNull(on.picked(ModelChoice(gpt5)))))
        assertEquals(before.copy(compareOn = true, compareWith = gpt5), repository.conversation(older))
        assertTrue(repository.choose(older, assertNotNull(comparing.picked(ModelChoice(gemini)))))

        assertEquals(comparing.copy(compareWith = gemini), repository.conversation(older).choice)
        assertEquals(writes + 6, database.writesInTransactions)
        assertEquals(listOf(newer, older), repository.observeRecentConversations().first().map { it.id })
        assertEquals(before.updatedAt, repository.conversation(older).updatedAt)
    }

    @Test
    fun withCompareOnButNoSecondModelASendIsAnsweredByTheModelAlone() = runTest {
        val repository = repository()

        val id = assertNotNull(repository.send(null, "first", newChat = ModelChoice(sonnet).toggledCompare()))
        advanceUntilIdle()

        assertEquals(ModelChoice(model = sonnet, compareOn = true), repository.conversation(id).choice)
        assertNull(repository.conversation(id).comparing)
        val reply = assertIs<Message.Assistant>(repository.thread(id).last())
        assertEquals(sonnet to answer(sonnet), reply.model to reply.blocks)
        assertNull(database.messageRows.value.last().pairSide)
        assertEquals(listOf(sonnet.id), fuelix.calls.map { it.model })
    }

    @Test
    fun aPersonaTurnsCompareOffAndTurningCompareOffDropsTheSecondModel() = runTest {
        val repository = repository()
        val first = assertNotNull(repository.send(null, "first", newChat = comparing)).also { advanceUntilIdle() }
        val second = assertNotNull(repository.send(null, "second", newChat = comparing)).also { advanceUntilIdle() }

        assertTrue(repository.choose(first, assertNotNull(comparing.picked(ModelChoice(terse)))))
        assertTrue(repository.choose(second, comparing.toggledCompare()))
        assertTrue(repository.choose(second, ModelChoice(model = gemini, persona = null, compareWith = gpt5)))

        assertEquals(ModelChoice(terse), repository.conversation(first).choice)
        assertEquals(gpt5.id to "terse", row(first).let { it.modelId to it.personaId })
        assertEquals(false to null, row(first).let { it.compareOn to it.compareModelId })
        assertEquals(ModelChoice(gemini), repository.conversation(second).choice)
        assertEquals(false to null, row(second).let { it.compareOn to it.compareModelId })
    }

    @Test
    fun aPersonaChoiceIsStoredWithCompareOffWhateverItSaysAboutCompare() = runTest {
        val repository = repository()

        val choice = ModelChoice(model = gpt5, persona = terse, compareOn = true, compareWith = sonnet)

        val id = assertNotNull(repository.send(null, "first", newChat = choice))
        advanceUntilIdle()

        assertEquals(ModelChoice(terse), repository.conversation(id).choice)
        assertEquals(false to null, row(id).let { it.compareOn to it.compareModelId })
        assertEquals(terse, assertIs<Message.Assistant>(repository.thread(id).last()).persona)
    }

    @Test
    fun aSecondModelThatIsTheModelItselfOrBlankIsRefusedAndChangesNothing() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "first")).also { advanceUntilIdle() }
        val writes = database.writes
        val itself = comparing.copy(compareWith = sonnet)
        val blank = comparing.copy(compareWith = gpt5.copy(id = " "))

        assertFalse(repository.choose(id, itself))
        assertFalse(repository.choose(id, blank))
        assertFalse(repository.choose(id + 1, comparing))
        assertNull(repository.send(null, "a new chat", newChat = itself))
        assertNull(repository.send(null, "a new chat", newChat = blank))

        assertEquals(writes, database.writes)
        assertEquals(ModelChoice(sonnet), repository.conversation(id).choice)
        assertEquals(listOf(id), database.conversationRows.value.map { it.id })
    }

    @Test
    fun aStoredCompareThatCannotPairIsAnsweredByASingleReply() = runTest {
        database = FakeChatDatabase(
            conversations = listOf(
                comparingRow(id = 1).copy(personaId = "terse"),
                comparingRow(id = 2).copy(compareModelId = " "),
                comparingRow(id = 3).copy(compareModelId = sonnet.id),
                comparingRow(id = 4).copy(compareModelId = "sonnet"),
            ),
        )
        val repository = repository()

        listOf(1L, 2, 3, 4).forEach { id ->
            assertEquals(id, repository.send(id, "hello"))
            advanceUntilIdle()
            assertIs<Message.Assistant>(repository.thread(id).last())
            assertNull(repository.conversation(id).comparing)
        }
        assertTrue(database.messageRows.value.all { it.pairSide == null })
        assertEquals(4, fuelix.calls.size)
    }

    @Test
    fun historyShowsTheComparedModelAsItsExtraAndAQueryFindsItOnlyWhileComparing() = runTest {
        val repository = repository()
        val single = assertNotNull(repository.send(null, "Postgres index")).also { advanceUntilIdle() }
        val compared = assertNotNull(repository.send(null, "SSE vs WebSocket", newChat = comparing))
        advanceUntilIdle()

        assertEquals(listOf(gpt5, null), repository.observeHistory().first().map { it.comparing })
        assertEquals(listOf(compared), repository.observeHistory("gpt-5").first().map { it.id })
        assertEquals(listOf(compared, single), repository.observeHistory("sonnet").first().map { it.id })

        assertTrue(repository.choose(compared, comparing.toggledCompare()))

        assertEquals(listOf(null, null), repository.observeHistory().first().map { it.comparing })
        assertTrue(repository.observeHistory("gpt-5").first().isEmpty())
    }

    private fun comparingRow(id: Long = 1) = ConversationEntity(
        id = id,
        title = "SSE vs WebSocket",
        modelId = sonnet.id,
        createdAt = 0,
        updatedAt = 0,
        compareOn = true,
        compareModelId = gpt5.id,
    )

    private fun stored(
        id: Long,
        role: MessageRole = MessageRole.ASSISTANT,
        status: MessageStatus,
        pairSide: Int? = null,
        text: String? = null,
        conversationId: Long = 1,
    ) = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        text = if (role == MessageRole.USER) "SSE or WebSocket?" else "",
        blocks = listOfNotNull(text?.let(StoredBlock::Paragraph)),
        modelId = if (role == MessageRole.USER) null else if (pairSide == PAIR_SECOND_SIDE) gpt5.id else sonnet.id,
        status = status,
        latencyMillis = null,
        createdAt = 0,
        pairSide = pairSide,
    )
}
