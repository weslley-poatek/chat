package br.com.weslleycampos.chat.core.database.daos

import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.core.database.inMemoryChatDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ForkDaoTest {
    private val database = inMemoryChatDatabase()
    private val dao = database.forkDao()
    private val conversations = database.conversationDao()
    private val messages = database.messageDao()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun theMessagesThroughOneAreTheConversationsUpToAndIncludingItInTheOrderTheyWereWritten() = runTest {
        val conversationId = conversations.insert(conversation())
        val other = conversations.insert(conversation())
        val first = messages.insert(userMessage(conversationId, text = "first", createdAt = 20))
        val elsewhere = messages.insert(userMessage(other, text = "elsewhere"))
        val second = messages.insert(reply(conversationId, createdAt = 10))
        val third = messages.insert(userMessage(conversationId, text = "third"))
        messages.insert(reply(conversationId))

        assertEquals(listOf(first, second, third), dao.messagesThrough(conversationId, third).map { it.id })
        assertEquals(listOf(first), dao.messagesThrough(conversationId, first).map { it.id })
        assertEquals(
            userMessage(conversationId, text = "first", createdAt = 20).copy(id = first),
            dao.messagesThrough(conversationId, first).single(),
        )
        assertEquals(listOf(first), dao.messagesThrough(conversationId, elsewhere).map { it.id })
        assertTrue(dao.messagesThrough(other, first).isEmpty())
        assertTrue(dao.messagesThrough(other + 10, third).isEmpty())
    }

    @Test
    fun insertedCopiesTakeNewIdsInTheirOrderAndKeepEveryOtherColumn() = runTest {
        val source = conversations.insert(conversation())
        val fork = conversations.insert(conversation(forkedFromTitle = "Hello", forkedAtMessage = 2))
        val partial = listOf(StoredBlock.Paragraph("Two li"))
        messages.insert(userMessage(source, text = "why?", createdAt = 5))
        messages.insert(reply(source, status = MessageStatus.INTERRUPTED, blocks = partial, createdAt = 5))
        val thread = dao.messagesThrough(source, messageId = Long.MAX_VALUE)

        dao.insertMessages(thread.map { it.copy(id = 0, conversationId = fork) })

        // The fork's thread reads in id order, so the same rows in the same order mean their new ids
        // follow the order they were given in.
        val copies = messages.observeByConversation(fork).first()
        assertEquals(thread.map { it.copy(id = 0, conversationId = fork) }, copies.map { it.copy(id = 0) })
        assertTrue(copies.none { copy -> thread.any { it.id == copy.id } })
        assertEquals(thread, messages.observeByConversation(source).first())
    }

    @Test
    fun copiesNeedAnExistingConversation() = runTest {
        val source = conversations.insert(conversation())
        messages.insert(userMessage(source))
        val thread = dao.messagesThrough(source, messageId = Long.MAX_VALUE)

        val failure = runCatching {
            dao.insertMessages(thread.map { it.copy(id = 0, conversationId = source + 1) })
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(messages.observeByConversation(source + 1).first().isEmpty())
    }

    @Test
    fun dismissingTheBannerRewritesOnlyItsFlagAndSaysWhetherItChangedAnything() = runTest {
        val forked = conversation(
            title = "Write a useDebounce hook",
            updatedAt = 2,
            forkedFromTitle = "Write a useDebounce hook",
            forkedAtMessage = 3,
        )
        val plain = conversations.insert(conversation(title = "plain", updatedAt = 1))
        val fork = conversations.insert(forked)
        val newer = conversations.insert(conversation(updatedAt = 3))

        assertEquals(1, dao.dismissBanner(fork))
        assertEquals(0, dao.dismissBanner(fork))
        assertEquals(0, dao.dismissBanner(plain))
        assertEquals(0, dao.dismissBanner(newer + 10))

        assertEquals(forked.copy(id = fork, forkBannerDismissed = true), conversations.get(fork))
        assertEquals(conversation(title = "plain", updatedAt = 1).copy(id = plain), conversations.get(plain))
        assertEquals(listOf(newer, fork, plain), conversations.observeRecent(limit = 3).first().map { it.id })
    }
}
