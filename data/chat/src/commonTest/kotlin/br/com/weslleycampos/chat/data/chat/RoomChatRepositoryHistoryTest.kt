package br.com.weslleycampos.chat.data.chat

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** History's side of [RoomChatRepository]: its list and search, pinning, archiving and deleting. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomChatRepositoryHistoryTest {
    private val database = FakeChatDatabase()

    /** Streams at the slowest pace and shortest thinking time, in the test's own scope. */
    private fun TestScope.repository(): RoomChatRepository {
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

    /** Starts a conversation with each of [texts], one after the other, each reply done, and returns their ids. */
    private suspend fun TestScope.started(repository: ChatRepository, vararg texts: String): List<Long> =
        texts.map { text -> assertNotNull(repository.send(null, text)).also { advanceUntilIdle() } }

    private suspend fun ChatRepository.history(query: String = ""): List<Long> =
        observeHistory(query).first().map(Conversation::id)

    private suspend fun ChatRepository.recent(): List<Long> = observeRecentConversations().first().map(Conversation::id)

    private suspend fun ChatRepository.reply(conversationId: Long): Message.Assistant =
        observeMessages(conversationId).first().filterIsInstance<Message.Assistant>().last()

    @Test
    fun historyIsEveryConversationThatIsNotArchivedTheMostRecentlyUpdatedFirst() = runTest {
        val repository = repository()
        val (first, second, third, fourth) = started(repository, "first", "second", "third", "fourth")

        repository.archive(second)
        repository.setPinned(first, pinned = true)

        assertEquals(listOf(fourth, third, first), repository.history())
        assertEquals(listOf(false, false, true), repository.observeHistory().first().map { it.pinned })
        repository.send(first, "back to the first")
        assertEquals(listOf(first, fourth, third), repository.history())
    }

    @Test
    fun aBlankQueryListsEverythingAndAQueryIsTrimmed() = runTest {
        val repository = repository()
        val (hook, index) = started(repository, "Write a useDebounce hook", "Postgres index")

        assertEquals(listOf(index, hook), repository.history(" \t\n "))
        assertEquals(listOf(hook), repository.history("  useDebounce \n"))
        assertEquals(listOf("%useDebounce%" to emptyList<String>()), database.searches)
    }

    @Test
    fun aQueryMatchesTitlesTheUsersOwnMessagesAndTheModelsShortNameWhateverTheirCase() = runTest {
        val repository = repository()
        val hook = assertNotNull(repository.send(null, "Write a useDebounce hook")).also { advanceUntilIdle() }
        val index = assertNotNull(repository.send(null, "Postgres index")).also { advanceUntilIdle() }
        repository.send(index, "Why is the partial JSONB index slow?")
        advanceUntilIdle()
        val o3 = ModelChoice(ModelCatalogue["o3"])
        val rust = assertNotNull(repository.send(null, "Rust ownership", newChat = o3)).also { advanceUntilIdle() }

        assertEquals(listOf(hook), repository.history("DEBOUNCE"))
        assertEquals(listOf(index), repository.history("jsonb"))
        assertEquals(listOf(rust), repository.history("O3"))
        assertEquals(listOf(index, hook), repository.history("sonnet-4"))
        assertEquals(emptyList(), repository.history("nothing like it"))
    }

    @Test
    fun aQueryDoesNotSearchTheCannedReplies() = runTest {
        val repository = repository()
        started(repository, "first", "second")

        assertTrue(CannedReplies.replies[0].toString().contains("probability"))
        assertEquals(emptyList(), repository.history("probability"))
    }

    @Test
    fun aQueryIsSearchedAsAnEscapedContainsPatternWithTheModelsWhoseShortNameHasIt() = runTest {
        val repository = repository()
        started(repository, "Cut costs by 50% now")

        repository.history(" Gpt ")
        repository.history("50%_off\\")

        assertEquals(
            listOf("%Gpt%" to listOf("gpt5"), """%50\%\_off\\%""" to emptyList()),
            database.searches,
        )
    }

    @Test
    fun historyEmitsOnEverySendPinArchiveAndDeleteAndOnlyWhenItChanges() = runTest {
        val repository = repository()
        val (first) = started(repository, "first")
        val emissions = mutableListOf<List<Pair<Long, Boolean>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeHistory().collect { list -> emissions += list.map { it.id to it.pinned } }
        }

        val second = assertNotNull(repository.send(null, "second"))
        advanceUntilIdle()
        repository.setPinned(first, pinned = true)
        repository.setPinned(first, pinned = true)
        repository.archive(second)
        repository.archive(second)
        repository.delete(first)
        repository.delete(first)

        assertEquals(
            listOf(
                listOf(first to false),
                listOf(second to false, first to false),
                listOf(second to false, first to true),
                listOf(first to true),
                emptyList(),
            ),
            emissions,
        )
    }

    @Test
    fun aSearchStaysQuietWhileRepliesStreamUnlessItsMatchesChange() = runTest {
        val repository = repository()
        val (first) = started(repository, "Write a useDebounce hook")
        val emissions = mutableListOf<List<Long>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeHistory("debounce").collect { list -> emissions += list.map { it.id } }
        }

        // A conversation the search does not match, and its reply's 186 ticks.
        val other = assertNotNull(repository.send(null, "Postgres index"))
        advanceUntilIdle()
        // Now it matches; its next reply streams into it without changing the matches again.
        repository.send(other, "and the debounce?")
        advanceUntilIdle()

        assertEquals(listOf(listOf(first), listOf(other, first)), emissions)
        assertEquals(1, database.searches.size)
    }

    @Test
    fun theCountIsTheConversationsHistoryListsWithoutAQuery() = runTest {
        val repository = repository()
        val counts = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeConversationCount().collect { counts += it }
        }

        val (first, second, third) = started(repository, "first", "second", "third")
        repository.setPinned(first, pinned = true)
        repository.archive(second)
        repository.delete(third)

        assertEquals(listOf(0, 1, 2, 3, 2, 1), counts)
        assertEquals(listOf(first), repository.history())
    }

    @Test
    fun pinningKeepsAConversationsPlaceAndUpdatedAtAndSaysWhetherItChangedAnything() = runTest {
        val repository = repository()
        val (older, newer) = started(repository, "older", "newer")
        val before = assertNotNull(repository.observeConversation(older).first())

        assertTrue(repository.setPinned(older, pinned = true))
        assertFalse(repository.setPinned(older, pinned = true))
        assertFalse(repository.setPinned(newer + 1, pinned = true))

        assertEquals(before.copy(pinned = true), repository.observeConversation(older).first())
        assertEquals(listOf(newer, older), repository.recent())
        assertEquals(listOf(newer, older), repository.history())
        assertTrue(repository.setPinned(older, pinned = false))
        assertEquals(before, repository.observeConversation(older).first())
    }

    @Test
    fun archivingLeavesAConversationOutOfHistoryAndRecentButKeepsItAndItsMessages() = runTest {
        val repository = repository()
        val (first, second, third, fourth) = started(repository, "first", "second", "third", "fourth")
        val messages = repository.observeMessages(third).first()

        assertTrue(repository.archive(third))
        assertFalse(repository.archive(third))
        assertFalse(repository.archive(fourth + 1))

        assertEquals(listOf(fourth, second, first), repository.recent())
        assertEquals(listOf(fourth, second, first), repository.history())
        assertEquals(emptyList(), repository.history("third"))
        assertEquals(true, repository.observeConversation(third).first()?.archived)
        assertEquals(messages, repository.observeMessages(third).first())
    }

    @Test
    fun aMessageSentInAnArchivedConversationBringsItBackToTheTopAsItWas() = runTest {
        val repository = repository()
        val (first, second) = started(repository, "first", "second")
        repository.setPinned(first, pinned = true)
        repository.archive(first)

        assertEquals(first, repository.send(first, "still here?"))

        val conversation = assertNotNull(repository.observeConversation(first).first())
        assertFalse(conversation.archived)
        assertTrue(conversation.pinned)
        assertEquals(listOf(first, second), repository.recent())
        assertEquals(listOf(first, second), repository.history())
    }

    @Test
    fun deletingAConversationRemovesItAndItsMessagesAndSaysWhetherItDeletedAnything() = runTest {
        val repository = repository()
        val (first, second) = started(repository, "first", "second")

        assertTrue(repository.delete(first))
        assertFalse(repository.delete(first))
        assertFalse(repository.delete(second + 1))

        assertNull(repository.observeConversation(first).first())
        assertTrue(repository.observeMessages(first).first().isEmpty())
        assertEquals(listOf(second), repository.recent())
        assertEquals(listOf(second), repository.history())
        assertNull(repository.send(first, "hello?"))
        assertEquals(listOf(second), repository.history())
    }

    @Test
    fun deletingAConversationWhileItsReplyThinksStopsTheReplyOnItsFirstTick() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "first"))
        advanceTimeBy(100)

        assertTrue(repository.delete(id))
        advanceUntilIdle()

        // Thinking ends at 320 ms; the first tick, at 348 ms, finds the reply gone and the stream stops.
        assertEquals(1, database.replyUpdates.size)
        assertEquals(320L + 28, currentTime)
        assertTrue(database.messageRows.value.isEmpty())
        val next = assertNotNull(repository.send(null, "a new chat"))
        advanceUntilIdle()
        assertEquals(ReplyStatus.DONE, repository.reply(next).status)
    }

    @Test
    fun deletingAConversationWhileItsReplyStreamsStopsItAndLeavesTheOthersStreaming() = runTest {
        val repository = repository()
        val deleted = assertNotNull(repository.send(null, "first"))
        val kept = assertNotNull(repository.send(null, "second"))
        advanceTimeBy(400)
        val deletedReply = repository.reply(deleted).id
        val writesBefore = database.replyUpdates.count { it.id == deletedReply }
        assertEquals(ReplyStatus.STREAMING, repository.reply(kept).status)

        assertTrue(repository.delete(deleted))
        advanceUntilIdle()

        assertEquals(writesBefore + 1, database.replyUpdates.count { it.id == deletedReply })
        assertEquals(ReplyStatus.DONE, repository.reply(kept).status)
        assertEquals(CannedReplies.replies[0], repository.reply(kept).blocks)
        assertEquals(listOf(kept), repository.history())
    }

    @Test
    fun archivingAConversationWhileItsReplyStreamsLetsTheReplyFinish() = runTest {
        val repository = repository()
        val id = assertNotNull(repository.send(null, "first"))
        advanceTimeBy(400)
        assertEquals(ReplyStatus.STREAMING, repository.reply(id).status)

        assertTrue(repository.archive(id))
        advanceUntilIdle()

        assertEquals(ReplyStatus.DONE, repository.reply(id).status)
        assertEquals(CannedReplies.replies[0], repository.reply(id).blocks)
        assertEquals(emptyList(), repository.history())
    }
}
