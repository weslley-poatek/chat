package br.com.weslleycampos.chat.core.database.daos

import br.com.weslleycampos.chat.core.database.inMemoryChatDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationDaoTest {
    private val database = inMemoryChatDatabase()
    private val dao = database.conversationDao()

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
}
