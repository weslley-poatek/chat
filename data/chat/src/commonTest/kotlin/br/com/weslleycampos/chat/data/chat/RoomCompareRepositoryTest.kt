package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
import br.com.weslleycampos.chat.data.chat.fakes.FixedRandom
import br.com.weslleycampos.chat.data.chat.fakes.TestClock
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.data.chat.replies.CannedReplies
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** "continue with this →": [RoomCompareRepository] next to the [RoomChatRepository] that writes the pairs. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomCompareRepositoryTest {
    private var database = FakeChatDatabase()

    private val sonnet = ModelCatalogue["sonnet"]
    private val gpt5 = ModelCatalogue["gpt5"]
    private val comparing = ModelChoice(model = sonnet, compareOn = true, compareWith = gpt5)

    /** Streams at the slowest pace and shortest thinking time, in the test's own scope. */
    private fun TestScope.chat(): RoomChatRepository {
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
                scope = this,
            ),
            streamer = ReplyStreamer(messageDao = database.messageDao, clock = clock, random = FixedRandom()),
            clock = clock,
            scope = this,
        )
    }

    private fun compare(): RoomCompareRepository = RoomCompareRepository(
        conversationDao = database.conversationDao,
        compareDao = database.compareDao,
        transactions = database.transactions,
    )

    private suspend fun ChatRepository.thread(conversationId: Long): List<Message> =
        observeMessages(conversationId).first()

    private suspend fun ChatRepository.pair(conversationId: Long): Message.ComparePair =
        assertIs<Message.ComparePair>(thread(conversationId).last())

    private suspend fun ChatRepository.conversation(id: Long): Conversation =
        assertNotNull(observeConversation(id).first())

    /** A conversation comparing sonnet with gpt-5, with one finished pair. */
    private suspend fun TestScope.compared(chat: ChatRepository, text: String = "SSE or WebSocket?"): Long =
        assertNotNull(chat.send(null, text, newChat = comparing)).also { advanceUntilIdle() }

    @Test
    fun continuingWithTheSecondSideMakesItsModelAnswerAloneAndKeepsThePlaceInRecentAndThePair() = runTest {
        val chat = chat()
        val id = compared(chat)
        val newer = assertNotNull(chat.send(null, "newer")).also { advanceUntilIdle() }
        val before = chat.conversation(id)
        val pair = chat.pair(id)
        advanceTimeBy(5.minutes)

        assertTrue(compare().continueWith(id, pairMessageId = pair.id, side = 1))

        assertEquals(before.copy(model = gpt5, compareOn = false, compareWith = null), chat.conversation(id))
        assertEquals(ModelChoice(gpt5), chat.conversation(id).choice)
        assertEquals(pair, chat.pair(id))
        assertEquals(listOf(newer, id), chat.observeRecentConversations().first().map { it.id })
        val row = database.conversationRows.value.single { it.id == id }
        assertEquals(Triple("gpt5", false, null), Triple(row.modelId, row.compareOn, row.compareModelId))
    }

    @Test
    fun continuingWithTheFirstSideKeepsTheModelAndTurnsCompareOff() = runTest {
        val chat = chat()
        val id = compared(chat)

        assertTrue(compare().continueWith(id, pairMessageId = chat.pair(id).id, side = 0))

        assertEquals(ModelChoice(sonnet), chat.conversation(id).choice)
        assertNull(chat.conversation(id).comparing)
    }

    @Test
    fun theNextSendAfterContinuingIsASingleReplyFromThatModelGoingOnWithTheCannedReplies() = runTest {
        val chat = chat()
        val id = compared(chat)
        assertTrue(compare().continueWith(id, pairMessageId = chat.pair(id).id, side = 1))

        assertEquals(id, chat.send(id, "and reconnects?"))
        advanceUntilIdle()

        val reply = assertIs<Message.Assistant>(chat.thread(id).last())
        assertEquals(gpt5, reply.model)
        assertEquals(CannedReplies.replies[1], reply.blocks)
        assertEquals(4, chat.thread(id).size)
    }

    @Test
    fun aDoneSideCanBeContinuedWithWhileTheOtherStillStreamsAndTheNextSendWaitsForIt() = runTest {
        val chat = chat()
        val id = assertNotNull(chat.send(null, "SSE or WebSocket?", newChat = comparing))
        val compare = compare()
        val pairId = chat.pair(id).id

        assertFalse(compare.continueWith(id, pairMessageId = pairId, side = 1))
        advanceTimeBy(320 + 80 * 28)
        assertEquals(listOf(ReplyStatus.STREAMING, ReplyStatus.DONE), chat.pair(id).sides.map { it.status })
        assertFalse(compare.continueWith(id, pairMessageId = pairId, side = 0))
        assertTrue(compare.continueWith(id, pairMessageId = pairId, side = 1))
        assertNull(chat.send(id, "too soon"))

        advanceUntilIdle()
        assertEquals(listOf(ReplyStatus.DONE, ReplyStatus.DONE), chat.pair(id).sides.map { it.status })
        assertEquals(ModelChoice(gpt5), chat.conversation(id).choice)
        assertEquals(id, chat.send(id, "now"))
    }

    @Test
    fun anInterruptedSideCannotBeContinuedWith() = runTest {
        database = FakeChatDatabase(
            conversations = listOf(comparingRow()),
            messages = listOf(
                stored(id = 1, role = MessageRole.USER),
                stored(id = 2, pairSide = PAIR_FIRST_SIDE, status = MessageStatus.INTERRUPTED),
                stored(id = 3, pairSide = PAIR_SECOND_SIDE),
            ),
        )
        val compare = compare()

        assertFalse(compare.continueWith(conversationId = 1, pairMessageId = 2, side = 0))
        assertTrue(compare.continueWith(conversationId = 1, pairMessageId = 2, side = 1))
        assertEquals("gpt5", database.conversationRows.value.single().modelId)
    }

    @Test
    fun aConversationPairOrSideThatIsNotThereOrAModelTheCatalogueNoLongerHasChangesNothing() = runTest {
        database = FakeChatDatabase(
            conversations = listOf(comparingRow(id = 1), comparingRow(id = 2)),
            messages = listOf(
                stored(id = 1, role = MessageRole.USER),
                stored(id = 2, pairSide = PAIR_FIRST_SIDE),
                stored(id = 3, pairSide = PAIR_SECOND_SIDE, modelId = "retired"),
                stored(id = 4, role = MessageRole.USER),
                stored(id = 5),
                stored(id = 6, role = MessageRole.USER, conversationId = 2),
                stored(id = 7, pairSide = PAIR_FIRST_SIDE, conversationId = 2),
                stored(id = 8, pairSide = PAIR_SECOND_SIDE, conversationId = 2),
            ),
        )
        val compare = compare()
        val before = database.conversationRows.value

        assertFalse(compare.continueWith(conversationId = 3, pairMessageId = 7, side = 0))
        assertFalse(compare.continueWith(conversationId = 1, pairMessageId = 7, side = 0))
        assertFalse(compare.continueWith(conversationId = 2, pairMessageId = 8, side = 0))
        assertFalse(compare.continueWith(conversationId = 1, pairMessageId = 1, side = 0))
        assertFalse(compare.continueWith(conversationId = 1, pairMessageId = 5, side = 0))
        assertFalse(compare.continueWith(conversationId = 1, pairMessageId = 2, side = 2))
        assertFalse(compare.continueWith(conversationId = 1, pairMessageId = 2, side = -1))
        assertFalse(compare.continueWith(conversationId = 1, pairMessageId = 2, side = 1))

        assertEquals(0, database.writes)
        assertEquals(before, database.conversationRows.value)
    }

    @Test
    fun continuingWritesTheModelAndTheCompareInOneTransaction() = runTest {
        val chat = chat()
        val id = compared(chat)
        val writes = database.writes
        val writesInTransactions = database.writesInTransactions

        assertTrue(compare().continueWith(id, pairMessageId = chat.pair(id).id, side = 1))

        assertEquals(writes + 2, database.writes)
        assertEquals(writesInTransactions + 2, database.writesInTransactions)
    }

    @Test
    fun continuingDropsHistorysExtraAndTheSearchByTheComparedModel() = runTest {
        val chat = chat()
        val id = compared(chat)
        assertEquals(gpt5, chat.observeHistory().first().single().comparing)
        assertEquals(listOf(id), chat.observeHistory("sonnet").first().map { it.id })

        assertTrue(compare().continueWith(id, pairMessageId = chat.pair(id).id, side = 0))

        assertNull(chat.observeHistory().first().single().comparing)
        assertTrue(chat.observeHistory("gpt-5").first().isEmpty())
    }

    private fun comparingRow(id: Long = 1) = ConversationEntity(
        id = id,
        title = "SSE vs WebSocket",
        modelId = "sonnet",
        createdAt = 0,
        updatedAt = 0,
        compareOn = true,
        compareModelId = "gpt5",
    )

    private fun stored(
        id: Long,
        role: MessageRole = MessageRole.ASSISTANT,
        status: MessageStatus = MessageStatus.DONE,
        pairSide: Int? = null,
        modelId: String = if (pairSide == PAIR_SECOND_SIDE) "gpt5" else "sonnet",
        conversationId: Long = 1,
    ) = MessageEntity(
        id = id,
        conversationId = conversationId,
        role = role,
        text = if (role == MessageRole.USER) "SSE or WebSocket?" else "",
        blocks = emptyList(),
        modelId = if (role == MessageRole.USER) null else modelId,
        status = status,
        latencyMillis = null,
        createdAt = 0,
        pairSide = pairSide,
    )
}
