package br.com.weslleycampos.chat.core.database

import br.com.weslleycampos.chat.core.database.daos.conversation
import br.com.weslleycampos.chat.core.database.daos.userMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionRunnerTest {
    private val database = inMemoryChatDatabase()
    private val runner = RoomTransactionRunner(database)
    private val conversations = database.conversationDao()
    private val messages = database.messageDao()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun writesAcrossDaosCommitTogether() = runTest {
        val id = runner.inWriteTransaction {
            val id = conversations.insert(conversation())
            messages.insert(userMessage(id))
            id
        }

        assertEquals(1, messages.observeByConversation(id).first().size)
        assertEquals(listOf(id), conversations.observeRecent(limit = 3).first().map { it.id })
    }

    @Test
    fun aBlockThatThrowsLeavesNoWriteBehind() = runTest {
        val failure = runCatching {
            runner.inWriteTransaction {
                val id = conversations.insert(conversation())
                messages.insert(userMessage(id))
                error("the second half failed")
            }
        }

        assertTrue(failure.isFailure)
        assertTrue(conversations.observeRecent(limit = 3).first().isEmpty())
    }
}
