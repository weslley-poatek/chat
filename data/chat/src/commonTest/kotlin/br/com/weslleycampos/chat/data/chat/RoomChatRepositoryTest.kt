package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FixedRandom
import br.com.weslleycampos.chat.data.chat.fakes.TEST_START
import br.com.weslleycampos.chat.data.chat.fakes.TestClock
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.data.chat.replies.CannedReplies
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RoomChatRepositoryTest {
    private var database = FakeChatDatabase()

    /**
     * Streams at the slowest pace and shortest thinking time, on the test's virtual time. Replies
     * stream in the test's own scope by default, since `advanceUntilIdle` skips `backgroundScope`.
     */
    private fun TestScope.repository(scope: CoroutineScope = this): RoomChatRepository {
        val clock = TestClock(testScheduler)
        return RoomChatRepository(
            conversationDao = database.conversationDao,
            messageDao = database.messageDao,
            transactions = database.transactions,
            streamer = ReplyStreamer(messageDao = database.messageDao, clock = clock, random = FixedRandom()),
            clock = clock,
            scope = scope,
        )
    }

    private suspend fun ChatRepository.replies(conversationId: Long): List<Message.Assistant> =
        observeMessages(conversationId).first().filterIsInstance<Message.Assistant>()

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
    fun aReplyGoesFromThinkingThroughStreamingToDoneAndEveryTickReachesObservers() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "why is my debounce stale?"))
        val replies = mutableListOf<Message.Assistant>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeMessages(id).collect { replies += it.last() as Message.Assistant }
        }

        advanceUntilIdle()

        // REPLIES[0] at the slowest pace: 22 + 95 + 6 + 22 + 41 ticks of 28 ms after 320 ms of thinking.
        // Every tick is written, but the list's last one only covers the break counted after its last
        // item, which shows nothing new, so observers get one emission fewer than there are ticks.
        val ticks = 186
        assertEquals(ticks, database.replyUpdates.size)
        assertEquals(
            listOf(ReplyStatus.THINKING) + List(ticks - 2) { ReplyStatus.STREAMING } + ReplyStatus.DONE,
            replies.map { it.status },
        )
        assertEquals(CannedReplies.replies[0], replies.last().blocks)
        assertEquals((ticks * 28).milliseconds, replies.last().latency)
        assertEquals(320L + ticks * 28, currentTime)
    }

    @Test
    fun aConversationsRepliesRotateThroughTheThreeCannedRepliesAndANewConversationStartsOver() = runTest {
        val repository = repository()
        val first = assertNotNull(repository.send(null, "one"))
        advanceUntilIdle()
        repeat(3) {
            repository.send(first, "again")
            advanceUntilIdle()
        }
        val second = assertNotNull(repository.send(null, "two"))
        advanceUntilIdle()

        val r = CannedReplies.replies
        assertEquals(listOf(r[0], r[1], r[2], r[0]), repository.replies(first).map { it.blocks })
        assertEquals(listOf(r[0]), repository.replies(second).map { it.blocks })
    }

    @Test
    fun aSendWhileTheLastReplyThinksOrStreamsSendsNothing() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "first"))

        assertNull(repository.send(id, "while thinking"))
        advanceTimeBy(400)
        assertEquals(ReplyStatus.STREAMING, repository.replies(id).single().status)
        assertNull(repository.send(id, "while streaming"))
        assertEquals(2, repository.observeMessages(id).first().size)

        advanceUntilIdle()
        assertEquals(id, repository.send(id, "after it is done"))
        assertEquals(4, repository.observeMessages(id).first().size)
    }

    @Test
    fun blankTextOrAConversationThatDoesNotExistSendsNothing() = runTest {
        val repository = repository()

        assertNull(repository.send(null, "  \n\t "))
        assertNull(repository.send(42, "hello"))

        assertTrue(database.conversationRows.value.isEmpty())
        assertTrue(database.messageRows.value.isEmpty())
        assertNull(repository.observeConversation(42).first())
        assertTrue(repository.observeMessages(42).first().isEmpty())
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
                ConversationEntity(id = 1, title = "t", modelId = "gpt5", createdAt = 0, updatedAt = 0),
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
        assertEquals(ModelCatalogue["gpt5"], interrupted.model)
        assertEquals(1L, repository.send(1, "try again"))
        advanceUntilIdle()
        assertEquals(CannedReplies.replies[1], repository.replies(1).last().blocks)
        assertEquals(ModelCatalogue["gpt5"], repository.replies(1).last().model)
    }

    @Test
    fun aReplyKeepsStreamingIntoItsConversationWhileANewChatSends() = runTest {
        val repository = repository()
        val first = assertNotNull(repository.send(null, "first"))
        advanceTimeBy(400)

        val second = assertNotNull(repository.send(null, "a new chat"))
        advanceUntilIdle()

        assertNotEquals(first, second)
        assertEquals(listOf(ReplyStatus.DONE), repository.replies(first).map { it.status })
        assertEquals(listOf(ReplyStatus.DONE), repository.replies(second).map { it.status })
        assertEquals(CannedReplies.replies[0], repository.replies(first).single().blocks)
        assertEquals(CannedReplies.replies[0], repository.replies(second).single().blocks)
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
    fun aReplyWhoseStreamIsCancelledIsLeftInterruptedWithWhatItRevealed() = runTest {
        val streams = CoroutineScope(coroutineContext + Job(coroutineContext.job))
        val repository = repository(scope = streams)
        val id = assertNotNull(repository.send(null, "first"))
        advanceTimeBy(400)

        streams.cancel()
        runCurrent()

        val reply = repository.replies(id).single()
        assertEquals(ReplyStatus.INTERRUPTED, reply.status)
        assertEquals(listOf(Block.Paragraph("Two ")), reply.blocks)
        assertNull(reply.latency)
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
        modelId = if (role == MessageRole.USER) null else "gpt5",
        status = status,
        latencyMillis = null,
        createdAt = 0,
    )
}
