package br.com.weslleycampos.chat.core.database.daos

import br.com.weslleycampos.chat.core.database.inMemoryChatDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationDaoTest {
    private val database = inMemoryChatDatabase()
    private val dao = database.conversationDao()
    private val history = database.historyDao()
    private val messages = database.messageDao()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun anInsertedConversationIsObservedByTheIdSqliteAssigned() = runTest {
        val id = dao.insert(conversation(title = "Write a useDebounce hook", updatedAt = 5))

        val observed = dao.observe(id).first()

        assertEquals(conversation(title = "Write a useDebounce hook", updatedAt = 5).copy(id = id), observed)
        assertEquals(observed, dao.get(id))
        assertNull(dao.observe(id + 1).first())
        assertNull(dao.get(id + 1))
    }

    @Test
    fun recentConversationsAreTheMostRecentlyUpdatedFirstUpToTheLimit() = runTest {
        val oldest = dao.insert(conversation(title = "oldest", updatedAt = 1))
        val tiedFirst = dao.insert(conversation(title = "tied first", updatedAt = 3))
        val newest = dao.insert(conversation(title = "newest", updatedAt = 4))
        val tiedSecond = dao.insert(conversation(title = "tied second", updatedAt = 3))

        assertEquals(listOf(newest, tiedSecond, tiedFirst), dao.observeRecent(limit = 3).first().map { it.id })
        assertEquals(listOf(newest, tiedSecond, tiedFirst, oldest), dao.observeRecent(limit = 10).first().map { it.id })
    }

    @Test
    fun settingUpdatedAtMovesAConversationToTheTopOfRecent() = runTest {
        val first = dao.insert(conversation(updatedAt = 1))
        val second = dao.insert(conversation(updatedAt = 2))

        dao.setUpdatedAt(first, updatedAt = 3)

        assertEquals(listOf(first, second), dao.observeRecent(limit = 3).first().map { it.id })
        assertEquals(3, dao.get(first)?.updatedAt)
        assertEquals(1, dao.get(first)?.createdAt)
    }

    @Test
    fun aNewConversationHasNoPersona() = runTest {
        val id = dao.insert(conversation())

        assertNull(dao.get(id)?.personaId)
    }

    @Test
    fun settingTheModelRewritesOnlyItsModelAndPersona() = runTest {
        val id = dao.insert(conversation(title = "Postgres index", updatedAt = 5))
        val other = dao.insert(conversation(title = "other", updatedAt = 6))

        val withPersona = dao.setModel(id, modelId = "gpt5", personaId = "terse")
        val afterPersona = dao.get(id)
        val withoutPersona = dao.setModel(id, modelId = "gemini", personaId = null)

        assertEquals(1, withPersona)
        assertEquals(1, withoutPersona)
        val expected = conversation(title = "Postgres index", updatedAt = 5).copy(id = id)
        assertEquals(expected.copy(modelId = "gpt5", personaId = "terse"), afterPersona)
        assertEquals(expected.copy(modelId = "gemini"), dao.get(id))
        assertEquals(conversation(title = "other", updatedAt = 6).copy(id = other), dao.get(other))
        assertEquals(listOf(other, id), dao.observeRecent(limit = 3).first().map { it.id })
    }

    @Test
    fun settingTheModelOfAConversationThatDoesNotExistChangesNothing() = runTest {
        val id = dao.insert(conversation())

        assertEquals(0, dao.setModel(id + 1, modelId = "gpt5", personaId = "terse"))
        assertEquals(conversation().copy(id = id), dao.get(id))
    }

    @Test
    fun aNewConversationIsNeitherPinnedNorArchived() = runTest {
        val id = dao.insert(conversation())

        assertFalse(dao.get(id)?.pinned ?: true)
        assertFalse(dao.get(id)?.archived ?: true)
    }

    @Test
    fun aNewConversationIsNoForkAndAForksOriginIsStoredWithIt() = runTest {
        val plain = dao.insert(conversation())
        val fork = dao.insert(
            conversation(title = "Postgres index", forkedFromTitle = "Postgres index", forkedAtMessage = 2),
        )

        val plainRow = dao.get(plain)
        assertNull(plainRow?.forkedFromTitle)
        assertNull(plainRow?.forkedAtMessage)
        assertFalse(plainRow?.forkBannerDismissed ?: true)
        val forkRow = dao.get(fork)
        assertEquals("Postgres index" to 2, forkRow?.forkedFromTitle to forkRow?.forkedAtMessage)
        assertFalse(forkRow?.forkBannerDismissed ?: true)
    }

    @Test
    fun recentConversationsLeaveTheArchivedOnesOut() = runTest {
        val kept = dao.insert(conversation(updatedAt = 1))
        dao.insert(conversation(updatedAt = 2, archived = true))
        val pinned = dao.insert(conversation(updatedAt = 3, pinned = true))

        assertEquals(listOf(pinned, kept), dao.observeRecent(limit = 3).first().map { it.id })
    }

    @Test
    fun pinningRewritesOnlyPinnedLeavesTheOrderAloneAndSaysWhetherItChangedAnything() = runTest {
        val id = dao.insert(conversation(title = "RFC: memory retention policy", updatedAt = 5))
        val newer = dao.insert(conversation(updatedAt = 6))

        assertEquals(1, dao.setPinned(id, pinned = true))
        assertEquals(0, dao.setPinned(id, pinned = true))
        val afterPin = dao.get(id)
        assertEquals(listOf(newer, id), history.observeAll().first().map { it.id })
        assertEquals(1, dao.setPinned(id, pinned = false))
        assertEquals(0, dao.setPinned(id + 10, pinned = true))

        val expected = conversation(title = "RFC: memory retention policy", updatedAt = 5).copy(id = id)
        assertEquals(expected.copy(pinned = true), afterPin)
        assertEquals(expected, dao.get(id))
    }

    @Test
    fun archivingHidesAConversationFromEveryListKeepsItAndItsMessagesAndSaysWhetherItChangedAnything() = runTest {
        val id = dao.insert(conversation(title = "Postgres index", updatedAt = 5, modelId = "gpt5", pinned = true))
        val kept = dao.insert(conversation(updatedAt = 1))
        messages.insert(userMessage(id))

        assertEquals(1, dao.setArchived(id, archived = true))
        assertEquals(0, dao.setArchived(id, archived = true))
        assertEquals(0, dao.setArchived(id + 10, archived = true))

        assertEquals(listOf(kept), history.observeAll().first().map { it.id })
        assertEquals(listOf(kept), dao.observeRecent(limit = 3).first().map { it.id })
        assertTrue(history.search(containsPattern("Postgres"), modelIds = listOf("gpt5")).first().isEmpty())
        assertEquals(1, history.observeCount().first())
        val archived = conversation(title = "Postgres index", updatedAt = 5, modelId = "gpt5", pinned = true)
        assertEquals(archived.copy(id = id, archived = true), dao.observe(id).first())
        assertEquals(1, messages.observeByConversation(id).first().size)

        assertEquals(1, dao.setArchived(id, archived = false))
        assertEquals(listOf(id, kept), history.observeAll().first().map { it.id })
    }

    @Test
    fun deletingAConversationDeletesItAndItsMessagesAndSaysWhetherItDeletedAnything() = runTest {
        val id = dao.insert(conversation())
        val kept = dao.insert(conversation())
        messages.insert(userMessage(id))
        messages.insert(reply(id))
        messages.insert(userMessage(kept))

        assertEquals(1, dao.delete(id))
        assertEquals(0, dao.delete(id))

        assertNull(dao.get(id))
        assertTrue(messages.observeByConversation(id).first().isEmpty())
        assertEquals(listOf(kept), history.observeAll().first().map { it.id })
        assertEquals(1, messages.observeByConversation(kept).first().size)
    }
}
