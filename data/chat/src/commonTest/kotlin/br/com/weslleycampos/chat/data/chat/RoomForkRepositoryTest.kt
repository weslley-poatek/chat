package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
import br.com.weslleycampos.chat.data.chat.fakes.FixedRandom
import br.com.weslleycampos.chat.data.chat.fakes.TEST_START
import br.com.weslleycampos.chat.data.chat.fakes.TestClock
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.ForkOrigin
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.data.chat.replies.CannedReplies
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** "⑂ fork here": [RoomForkRepository] next to the [RoomChatRepository] that writes the threads it forks. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomForkRepositoryTest {
    private var database = FakeChatDatabase()
    private val reviewer: Persona = PersonaPresets.personas.single { it.id == "reviewer" }

    /** Streams at the slowest pace and shortest thinking time, in the test's own scope. */
    private fun TestScope.chat(): RoomChatRepository {
        val clock = TestClock(testScheduler)
        return RoomChatRepository(
            conversationDao = database.conversationDao,
            historyDao = database.historyDao,
            messageDao = database.messageDao,
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

    private fun TestScope.forks(): RoomForkRepository = RoomForkRepository(
        conversationDao = database.conversationDao,
        forkDao = database.forkDao,
        transactions = database.transactions,
        clock = TestClock(testScheduler),
    )

    /** A new conversation with one turn per text, each reply done: twice as many messages as [texts]. */
    private suspend fun TestScope.conversationOf(
        chat: ChatRepository,
        vararg texts: String,
        newChat: ModelChoice? = null,
    ): Long {
        val id = assertNotNull(chat.send(null, texts.first(), newChat))
        advanceUntilIdle()
        texts.drop(1).forEach { text ->
            assertEquals(id, chat.send(id, text))
            advanceUntilIdle()
        }
        return id
    }

    private suspend fun ChatRepository.thread(conversationId: Long): List<Message> =
        observeMessages(conversationId).first()

    private suspend fun ChatRepository.conversation(id: Long): Conversation =
        assertNotNull(observeConversation(id).first())

    /** [this] as it reads in [conversationId], its id left out. */
    private fun Message.movedTo(conversationId: Long): Message = when (this) {
        is Message.User -> copy(id = 0, conversationId = conversationId)
        is Message.Assistant -> copy(id = 0, conversationId = conversationId)
    }

    @Test
    fun aForkCopiesTheThreadUpToTheMessageInOrderWithTheSameContentUnderNewIds() = runTest {
        val chat = chat()
        val source = conversationOf(
            chat,
            "Write a useDebounce hook",
            "Add a leading option",
            "Now cancel on unmount",
            newChat = ModelChoice(reviewer),
        )
        val thread = chat.thread(source)

        val fork = assertNotNull(forks().fork(source, messageId = thread[3].id))

        val copies = chat.thread(fork)
        assertNotEquals(source, fork)
        assertEquals(thread.take(4).map { it.movedTo(fork) }, copies.map { it.movedTo(fork) })
        assertEquals(listOf(reviewer, reviewer), copies.filterIsInstance<Message.Assistant>().map { it.persona })
        assertTrue(copies.none { copy -> thread.any { it.id == copy.id } })
    }

    @Test
    fun aForkIsANewConversationOnTheSourcesTitleModelAndPersonaCreatedNowNeitherPinnedNorArchived() = runTest {
        val chat = chat()
        val reviewed = ModelChoice(reviewer)
        val source = conversationOf(chat, "Write a useDebounce hook", "Add a leading option", newChat = reviewed)
        chat.setPinned(source, pinned = true)
        chat.archive(source)
        advanceTimeBy(5.minutes)

        val fork = assertNotNull(forks().fork(source, messageId = chat.thread(source)[2].id))

        val now = TEST_START + currentTime.milliseconds
        assertEquals(
            Conversation(
                id = fork,
                title = "Write a useDebounce hook",
                model = reviewer.model,
                persona = reviewer,
                createdAt = now,
                updatedAt = now,
                fork = ForkOrigin(sourceTitle = "Write a useDebounce hook", messageNumber = 3),
            ),
            chat.conversation(fork),
        )
    }

    @Test
    fun aForkRecordsTheSourcesTitleAndThePositionOfTheMessageCountingFromOne() = runTest {
        val chat = chat()
        val forks = forks()
        val source = conversationOf(chat, "Write a useDebounce hook", "Add a leading option")
        val thread = chat.thread(source)

        val origins = thread.indices.map { index ->
            chat.conversation(assertNotNull(forks.fork(source, messageId = thread[index].id))).fork
        }
        val first = assertNotNull(forks.fork(source, messageId = thread[1].id))
        val ofTheFork = assertNotNull(forks.fork(first, messageId = chat.thread(first).first().id))

        assertEquals((1..4).map { ForkOrigin(sourceTitle = "Write a useDebounce hook", messageNumber = it) }, origins)
        assertNull(chat.conversation(source).fork)
        assertEquals(
            ForkOrigin(sourceTitle = "Write a useDebounce hook", messageNumber = 1),
            chat.conversation(ofTheFork).fork,
        )
        assertEquals(1, chat.thread(ofTheFork).size)
    }

    @Test
    fun theSourceIsLeftAsItWas() = runTest {
        val chat = chat()
        val source = conversationOf(chat, "Write a useDebounce hook", "Add a leading option")
        val other = conversationOf(chat, "Postgres index")
        val conversationBefore = database.conversationRows.value.single { it.id == source }
        val messagesBefore = database.messageRows.value.filter { it.conversationId == source }
        advanceTimeBy(5.minutes)

        val fork = assertNotNull(forks().fork(source, messageId = messagesBefore[1].id))

        assertEquals(conversationBefore, database.conversationRows.value.single { it.id == source })
        assertEquals(messagesBefore, database.messageRows.value.filter { it.conversationId == source })
        assertEquals(listOf(fork, other, source), chat.observeRecentConversations().first().map(Conversation::id))
    }

    @Test
    fun theBannerReadsTheSameAfterTheSourceIsDeletedAndTheForkKeepsItsThread() = runTest {
        val chat = chat()
        val source = conversationOf(chat, "Write a useDebounce hook", "Add a leading option")
        val thread = chat.thread(source)
        val fork = assertNotNull(forks().fork(source, messageId = thread[3].id))

        assertTrue(chat.delete(source))

        assertEquals(
            ForkOrigin(sourceTitle = "Write a useDebounce hook", messageNumber = 4),
            chat.conversation(fork).fork,
        )
        assertEquals(thread.map { it.movedTo(fork) }, chat.thread(fork).map { it.movedTo(fork) })
    }

    @Test
    fun anUnknownConversationOrAMessageThatIsNotOneOfItsForksNothing() = runTest {
        val chat = chat()
        val forks = forks()
        val source = conversationOf(chat, "Write a useDebounce hook")
        val other = conversationOf(chat, "Postgres index")
        val writes = database.writes
        val lastMessage = chat.thread(other).last().id

        assertNull(forks.fork(conversationId = other + 10, messageId = lastMessage))
        assertNull(forks.fork(source, messageId = lastMessage + 10))
        assertNull(forks.fork(source, messageId = chat.thread(other).first().id))

        assertEquals(writes, database.writes)
        assertEquals(listOf(other, source), chat.observeHistory().first().map(Conversation::id))
    }

    @Test
    fun aReplyOnItsWayIsNeverCopiedButTheMessageBeforeItForksWithoutIt() = runTest {
        val chat = chat()
        val forks = forks()
        val source = assertNotNull(chat.send(null, "Write a useDebounce hook"))
        val (user, reply) = chat.thread(source)

        assertNull(forks.fork(source, messageId = reply.id))
        advanceTimeBy(400)
        assertEquals(ReplyStatus.STREAMING, (chat.thread(source).last() as Message.Assistant).status)
        assertNull(forks.fork(source, messageId = reply.id))
        val atTheUser = assertNotNull(forks.fork(source, messageId = user.id))
        advanceUntilIdle()

        assertEquals(listOf(user.movedTo(atTheUser)), chat.thread(atTheUser).map { it.movedTo(atTheUser) })
        assertEquals(ReplyStatus.DONE, (chat.thread(source).last() as Message.Assistant).status)
        val atTheReply = assertNotNull(forks.fork(source, messageId = reply.id))
        assertEquals(
            chat.thread(source).map { it.movedTo(atTheReply) },
            chat.thread(atTheReply).map { it.movedTo(atTheReply) },
        )
    }

    @Test
    fun anInterruptedReplyIsCopiedInterruptedWithWhatItRevealed() = runTest {
        database = FakeChatDatabase(
            conversations = listOf(
                ConversationEntity(id = 1, title = "Stale closure", modelId = "gpt5", createdAt = 0, updatedAt = 0),
            ),
            messages = listOf(
                message(id = 1, role = MessageRole.USER, status = MessageStatus.DONE),
                message(
                    id = 2,
                    role = MessageRole.ASSISTANT,
                    status = MessageStatus.INTERRUPTED,
                    blocks = listOf(StoredBlock.Paragraph("Two li")),
                ),
            ),
        )
        val chat = chat()

        val fork = assertNotNull(forks().fork(conversationId = 1, messageId = 2))

        val copy = chat.thread(fork).last() as Message.Assistant
        assertEquals(ReplyStatus.INTERRUPTED, copy.status)
        assertEquals(listOf(Block.Paragraph("Two li")), copy.blocks)
        assertNull(copy.latency)
        assertEquals(fork, chat.send(fork, "try again"))
    }

    @Test
    fun aForkAtAUserMessageEndsOnItAndASendGoesOnWithTheNextCannedReply() = runTest {
        val chat = chat()
        val source = conversationOf(chat, "Write a useDebounce hook", "Add a leading option")
        val thread = chat.thread(source)

        val fork = assertNotNull(forks().fork(source, messageId = thread[2].id))
        advanceUntilIdle()

        assertEquals(thread.take(3).map { it.movedTo(fork) }, chat.thread(fork).map { it.movedTo(fork) })
        assertEquals(fork, chat.send(fork, "Now cancel on unmount"))
        advanceUntilIdle()
        val replies = chat.thread(fork).filterIsInstance<Message.Assistant>()
        assertEquals(listOf(CannedReplies.replies[0], CannedReplies.replies[1]), replies.map { it.blocks })
        assertEquals(5, chat.thread(fork).size)
        assertEquals(4, chat.thread(source).size)
    }

    @Test
    fun aForkTopsHistoryAndRecentAndTheSearchFindsItsCopiedMessages() = runTest {
        val chat = chat()
        val hook = conversationOf(chat, "Write a useDebounce hook", "Add a leading option")
        val index = conversationOf(chat, "Postgres index")

        val fork = assertNotNull(forks().fork(hook, messageId = chat.thread(hook)[2].id))

        assertEquals(listOf(fork, index, hook), chat.observeHistory().first().map(Conversation::id))
        assertEquals(listOf(fork, index, hook), chat.observeRecentConversations().first().map(Conversation::id))
        assertEquals(listOf(fork, hook), chat.observeHistory("leading").first().map(Conversation::id))
        assertEquals(3, chat.observeConversationCount().first())
    }

    @Test
    fun dismissingTheBannerIsKeptAndSaysWhetherItChangedAnything() = runTest {
        val chat = chat()
        val forks = forks()
        val source = conversationOf(chat, "Write a useDebounce hook")
        val fork = assertNotNull(forks.fork(source, messageId = chat.thread(source).first().id))
        val newer = conversationOf(chat, "Postgres index")
        val updatedAt = chat.conversation(fork).updatedAt

        assertTrue(forks.dismissForkBanner(fork))
        assertFalse(forks.dismissForkBanner(fork))
        assertFalse(forks.dismissForkBanner(source))
        assertFalse(forks.dismissForkBanner(newer + 10))

        val dismissed = ForkOrigin(sourceTitle = "Write a useDebounce hook", messageNumber = 1, bannerDismissed = true)
        assertEquals(dismissed, chat.conversation(fork).fork)
        assertEquals(dismissed, chat().conversation(fork).fork)
        assertEquals(updatedAt, chat.conversation(fork).updatedAt)
        assertEquals(listOf(newer, fork, source), chat.observeRecentConversations().first().map(Conversation::id))
        assertNull(chat.conversation(source).fork)
    }

    @Test
    fun aForkWritesItsConversationAndItsMessagesInOneTransaction() = runTest {
        val chat = chat()
        val source = conversationOf(chat, "Write a useDebounce hook", "Add a leading option")
        val writes = database.writes
        val writesInTransactions = database.writesInTransactions

        assertNotNull(forks().fork(source, messageId = chat.thread(source).last().id))

        assertEquals(writes + 2, database.writes)
        assertEquals(writesInTransactions + 2, database.writesInTransactions)
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
