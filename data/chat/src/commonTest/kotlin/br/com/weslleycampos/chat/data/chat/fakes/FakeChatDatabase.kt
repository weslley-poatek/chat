package br.com.weslleycampos.chat.data.chat.fakes

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.ReplyProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private val unfinished = setOf(MessageStatus.THINKING, MessageStatus.STREAMING)

/**
 * The two DAOs and a transaction runner over in-memory tables. Like Room, every observer re-reads
 * its table whenever any row of it changes, and ids are assigned in insertion order.
 */
internal class FakeChatDatabase(
    conversations: List<ConversationEntity> = emptyList(),
    messages: List<MessageEntity> = emptyList(),
) {
    val conversationRows = MutableStateFlow(conversations)
    val messageRows = MutableStateFlow(messages)

    /** Every reply write, in order. */
    val replyUpdates = mutableListOf<ReplyProgress>()

    /** Runs before each reply write is applied; throw from it to fail that write. */
    var beforeReplyUpdate: (ReplyProgress) -> Unit = {}

    /** Writes so far, and how many of them ran inside [transactions]. */
    var writes = 0
        private set
    var writesInTransactions = 0
        private set
    private var inTransaction = false

    private var nextConversationId = (conversations.maxOfOrNull { it.id } ?: 0) + 1
    private var nextMessageId = (messages.maxOfOrNull { it.id } ?: 0) + 1

    private fun write() {
        writes++
        if (inTransaction) writesInTransactions++
    }

    val transactions = object : TransactionRunner {
        override suspend fun <R> inWriteTransaction(block: suspend () -> R): R {
            inTransaction = true
            try {
                return block()
            } finally {
                inTransaction = false
            }
        }
    }

    val conversationDao = object : ConversationDao {
        override fun observe(id: Long): Flow<ConversationEntity?> =
            conversationRows.map { rows -> rows.firstOrNull { it.id == id } }

        override fun observeRecent(limit: Int): Flow<List<ConversationEntity>> = conversationRows.map { rows ->
            rows.sortedWith(compareByDescending<ConversationEntity> { it.updatedAt }.thenByDescending { it.id })
                .take(limit)
        }

        override suspend fun get(id: Long): ConversationEntity? = conversationRows.value.firstOrNull { it.id == id }

        override suspend fun insert(conversation: ConversationEntity): Long {
            write()
            val id = nextConversationId++
            conversationRows.update { it + conversation.copy(id = id) }
            return id
        }

        override suspend fun setUpdatedAt(id: Long, updatedAt: Long) {
            write()
            conversationRows.update { rows -> rows.map { if (it.id == id) it.copy(updatedAt = updatedAt) else it } }
        }
    }

    val messageDao = object : MessageDao {
        override fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>> =
            messageRows.map { rows -> rows.filter { it.conversationId == conversationId }.sortedBy { it.id } }

        override suspend fun insert(message: MessageEntity): Long {
            check(conversationRows.value.any { it.id == message.conversationId }) { "FOREIGN KEY constraint failed" }
            write()
            val id = nextMessageId++
            messageRows.update { it + message.copy(id = id) }
            return id
        }

        override suspend fun updateReply(progress: ReplyProgress) {
            beforeReplyUpdate(progress)
            write()
            replyUpdates += progress
            messageRows.update { rows ->
                rows.map {
                    if (it.id != progress.id) return@map it
                    it.copy(blocks = progress.blocks, status = progress.status, latencyMillis = progress.latencyMillis)
                }
            }
        }

        override suspend fun countReplies(conversationId: Long): Int =
            messageRows.value.count { it.conversationId == conversationId && it.role == MessageRole.ASSISTANT }

        override suspend fun hasUnfinishedReply(conversationId: Long): Boolean =
            messageRows.value.any { it.conversationId == conversationId && it.status in unfinished }

        override suspend fun interruptReply(id: Long) {
            interrupt { it.id == id }
        }

        override suspend fun interruptUnfinishedReplies(): Int = interrupt { true }

        private fun interrupt(predicate: (MessageEntity) -> Boolean): Int {
            val matches = { row: MessageEntity -> predicate(row) && row.status in unfinished }
            val count = messageRows.value.count(matches)
            if (count == 0) return 0
            write()
            messageRows.update { rows ->
                rows.map { row -> row.copy(status = if (matches(row)) MessageStatus.INTERRUPTED else row.status) }
            }
            return count
        }
    }

    fun message(id: Long): MessageEntity = messageRows.value.single { it.id == id }
}
