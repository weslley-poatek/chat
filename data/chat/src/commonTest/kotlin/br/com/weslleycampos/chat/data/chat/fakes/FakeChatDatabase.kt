package br.com.weslleycampos.chat.data.chat.fakes

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.ForkDao
import br.com.weslleycampos.chat.core.database.daos.HistoryDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.daos.PersonaDao
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PersonaEntity
import br.com.weslleycampos.chat.core.database.entities.ReplyProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private val unfinished = setOf(MessageStatus.THINKING, MessageStatus.STREAMING)

private val newestFirst = compareByDescending<ConversationEntity> { it.updatedAt }.thenByDescending { it.id }

/**
 * The text a `containsPattern` LIKE pattern looks for: its `%` ends dropped and its `\` escapes
 * undone. Like SQLite's `LIKE` on ASCII text, the fake then matches it ignoring case.
 */
private fun containedText(pattern: String): String {
    require(pattern.length >= 2 && pattern.first() == '%' && pattern.last() == '%') {
        "Not a contains pattern: $pattern"
    }
    return buildString {
        var escaped = false
        pattern.substring(1, pattern.length - 1).forEach { char ->
            if (!escaped && char == '\\') {
                escaped = true
            } else {
                append(char)
                escaped = false
            }
        }
    }
}

/**
 * The five DAOs and a transaction runner over in-memory tables. Like Room, every observer re-reads
 * its table whenever any row of it changes, and ids are assigned in insertion order.
 */
internal class FakeChatDatabase(
    conversations: List<ConversationEntity> = emptyList(),
    messages: List<MessageEntity> = emptyList(),
    personas: List<PersonaEntity> = emptyList(),
) {
    val conversationRows = MutableStateFlow(conversations)
    val messageRows = MutableStateFlow(messages)
    val personaRows = MutableStateFlow(personas)

    /** How many times personas were inserted, kept apart from [writes] so a seed does not count as a send's. */
    var personaInserts = 0
        private set

    /** Every reply write, in order, a write to a reply that is gone included. */
    val replyUpdates = mutableListOf<ReplyProgress>()

    /** The id of every conversation deleted, in order. */
    val deletes = mutableListOf<Long>()

    /** The pattern and model ids of every History search, in order. */
    val searches = mutableListOf<Pair<String, List<String>>>()

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
            rows.filterNot { it.archived }.sortedWith(newestFirst).take(limit)
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

        override suspend fun setModel(id: Long, modelId: String, personaId: String?): Int {
            if (conversationRows.value.none { it.id == id }) return 0
            write()
            conversationRows.update { rows ->
                rows.map { if (it.id == id) it.copy(modelId = modelId, personaId = personaId) else it }
            }
            return 1
        }

        override suspend fun setPinned(id: Long, pinned: Boolean): Int =
            rewrite(id, { it.pinned != pinned }) { it.copy(pinned = pinned) }

        override suspend fun setArchived(id: Long, archived: Boolean): Int =
            rewrite(id, { it.archived != archived }) { it.copy(archived = archived) }

        /** Deletes the conversation and, like the foreign key's cascade, its messages. */
        override suspend fun delete(id: Long): Int {
            if (conversationRows.value.none { it.id == id }) return 0
            write()
            deletes += id
            messageRows.update { rows -> rows.filterNot { it.conversationId == id } }
            conversationRows.update { rows -> rows.filterNot { it.id == id } }
            return 1
        }

        private fun rewrite(
            id: Long,
            changes: (ConversationEntity) -> Boolean,
            update: (ConversationEntity) -> ConversationEntity,
        ): Int {
            if (conversationRows.value.none { it.id == id && changes(it) }) return 0
            write()
            conversationRows.update { rows -> rows.map { if (it.id == id) update(it) else it } }
            return 1
        }
    }

    val forkDao = object : ForkDao {
        override suspend fun messagesThrough(conversationId: Long, messageId: Long): List<MessageEntity> =
            messageRows.value.filter { it.conversationId == conversationId && it.id <= messageId }.sortedBy { it.id }

        /** One write for the lot, as Room's one statement per row inside the caller's transaction. */
        override suspend fun insertMessages(messages: List<MessageEntity>) {
            val conversationIds = conversationRows.value.map { it.id }.toSet()
            check(messages.all { it.conversationId in conversationIds }) { "FOREIGN KEY constraint failed" }
            write()
            val inserted = messages.map { it.copy(id = nextMessageId++) }
            messageRows.update { it + inserted }
        }

        override suspend fun dismissBanner(id: Long): Int {
            val showing = { row: ConversationEntity ->
                row.id == id && row.forkedFromTitle != null && !row.forkBannerDismissed
            }
            if (conversationRows.value.none(showing)) return 0
            write()
            conversationRows.update { rows ->
                rows.map { if (showing(it)) it.copy(forkBannerDismissed = true) else it }
            }
            return 1
        }
    }

    val historyDao = object : HistoryDao {
        override fun observeAll(): Flow<List<ConversationEntity>> =
            conversationRows.map { rows -> rows.filterNot { it.archived }.sortedWith(newestFirst) }

        override fun search(pattern: String, modelIds: List<String>): Flow<List<ConversationEntity>> {
            searches += pattern to modelIds
            val text = containedText(pattern)
            return combine(conversationRows, messageRows) { rows, messages ->
                val byMessage = messages
                    .filter { it.role == MessageRole.USER && it.text.contains(text, ignoreCase = true) }
                    .map { it.conversationId }
                    .toSet()
                rows.filter { row ->
                    !row.archived &&
                        (row.title.contains(text, ignoreCase = true) || row.modelId in modelIds || row.id in byMessage)
                }.sortedWith(newestFirst)
            }
        }

        override fun observeCount(): Flow<Int> = conversationRows.map { rows -> rows.count { !it.archived } }
    }

    val personaDao = object : PersonaDao {
        override fun observeAll(): Flow<List<PersonaEntity>> =
            personaRows.map { rows -> rows.sortedWith(compareBy<PersonaEntity> { it.position }.thenBy { it.id }) }

        override suspend fun insertIfAbsent(personas: List<PersonaEntity>) {
            personaInserts++
            personaRows.update { rows -> rows + personas.filter { new -> rows.none { it.id == new.id } } }
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

        override suspend fun updateReply(progress: ReplyProgress): Int {
            beforeReplyUpdate(progress)
            replyUpdates += progress
            if (messageRows.value.none { it.id == progress.id }) return 0
            write()
            messageRows.update { rows ->
                rows.map {
                    if (it.id != progress.id) return@map it
                    it.copy(blocks = progress.blocks, status = progress.status, latencyMillis = progress.latencyMillis)
                }
            }
            return 1
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
