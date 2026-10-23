package br.com.weslleycampos.chat.core.database.daos

import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.core.database.inMemoryChatDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompareDaoTest {
    private val database = inMemoryChatDatabase()
    private val dao = database.compareDao()
    private val conversations = database.conversationDao()
    private val messages = database.messageDao()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aNewConversationIsNotComparingAndACompareChoiceIsStoredWithIt() = runTest {
        val plain = conversations.insert(conversation())
        val comparing = conversations.insert(conversation(compareOn = true, compareModelId = "gpt5"))
        val waiting = conversations.insert(conversation(compareOn = true))

        val plainRow = conversations.get(plain)
        assertFalse(plainRow?.compareOn ?: true)
        assertNull(plainRow?.compareModelId)
        assertEquals(true to "gpt5", conversations.get(comparing)?.let { it.compareOn to it.compareModelId })
        assertEquals(true to null, conversations.get(waiting)?.let { it.compareOn to it.compareModelId })
    }

    @Test
    fun settingTheCompareRewritesOnlyItsTwoColumnsLeavesTheOrderAloneAndSaysWhetherItFoundTheConversation() =
        runTest {
            val id = conversations.insert(conversation(title = "SSE vs WebSocket", updatedAt = 5, modelId = "gemini"))
            val newer = conversations.insert(conversation(updatedAt = 6))

            assertEquals(1, dao.setCompare(id, compareOn = true, compareModelId = null))
            val turnedOn = conversations.get(id)
            assertEquals(1, dao.setCompare(id, compareOn = true, compareModelId = "gpt5"))
            val withSecond = conversations.get(id)
            assertEquals(0, dao.setCompare(newer + 10, compareOn = true, compareModelId = "gpt5"))

            val expected = conversation(title = "SSE vs WebSocket", updatedAt = 5, modelId = "gemini").copy(id = id)
            assertEquals(expected.copy(compareOn = true), turnedOn)
            assertEquals(expected.copy(compareOn = true, compareModelId = "gpt5"), withSecond)
            assertEquals(1, dao.setCompare(id, compareOn = false, compareModelId = null))
            assertEquals(expected, conversations.get(id))
            assertEquals(conversation(updatedAt = 6).copy(id = newer), conversations.get(newer))
            assertEquals(listOf(newer, id), conversations.observeRecent(limit = 3).first().map { it.id })
        }

    @Test
    fun aPairFromItsFirstSideIsItsTwoSidesInTheOrderTheyWereWritten() = runTest {
        val conversationId = conversations.insert(conversation())
        val other = conversations.insert(conversation())
        val question = messages.insert(userMessage(conversationId, text = "SSE or WebSocket?"))
        val partial = listOf(StoredBlock.Paragraph("Most li"))
        val first = messages.insert(pairSide(conversationId, PAIR_FIRST_SIDE, status = MessageStatus.STREAMING))
        messages.insert(userMessage(other, text = "elsewhere"))
        val second = messages.insert(
            pairSide(conversationId, PAIR_SECOND_SIDE, status = MessageStatus.INTERRUPTED, blocks = partial),
        )
        val next = messages.insert(userMessage(conversationId, text = "and reconnects?"))
        val elsewhere = messages.observeByConversation(other).first().single().id

        val pair = dao.pairFrom(conversationId, firstSideId = first)

        assertEquals(
            listOf(
                pairSide(conversationId, PAIR_FIRST_SIDE, status = MessageStatus.STREAMING).copy(id = first),
                pairSide(conversationId, PAIR_SECOND_SIDE, status = MessageStatus.INTERRUPTED, blocks = partial)
                    .copy(id = second),
            ),
            pair,
        )
        assertEquals(listOf(question, first), dao.pairFrom(conversationId, firstSideId = question).map { it.id })
        assertEquals(listOf(next), dao.pairFrom(conversationId, firstSideId = next).map { it.id })
        assertEquals(listOf(elsewhere), dao.pairFrom(other, firstSideId = first).map { it.id })
        assertTrue(dao.pairFrom(other, firstSideId = next).isEmpty())
    }
}
