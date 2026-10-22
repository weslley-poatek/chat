package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.ConversationTitles
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.replies.CannedReplies
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * [ChatRepository] on Room. One instance per process: when it is created it marks every reply
 * still thinking or streaming in the database interrupted, since no stream from an earlier run is
 * alive any more, and every read and send waits for that first.
 *
 * Replies stream in [scope], which outlives any screen.
 */
internal class RoomChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val transactions: TransactionRunner,
    private val streamer: ReplyStreamer,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : ChatRepository {
    private val recovery: Deferred<Int> = scope.async { messageDao.interruptUnfinishedReplies() }

    /** Serialises sends, so two sends to one conversation cannot both see it idle. */
    private val sending = Mutex()

    override fun observeConversation(id: Long): Flow<Conversation?> = afterRecovery {
        conversationDao.observe(id).map { it?.toConversation() }
    }

    override fun observeMessages(conversationId: Long): Flow<List<Message>> = afterRecovery {
        messageDao.observeByConversation(conversationId).map { rows -> rows.map(MessageEntity::toMessage) }
    }

    override fun observeRecentConversations(): Flow<List<Conversation>> = afterRecovery {
        conversationDao.observeRecent(ChatRepository.RECENT_CONVERSATIONS).map { rows ->
            rows.map(ConversationEntity::toConversation)
        }
    }

    override suspend fun send(conversationId: Long?, text: String): Long? {
        val message = text.trim()
        if (message.isEmpty()) return null
        recovery.await()
        val turn = sending.withLock {
            transactions.inWriteTransaction { startTurn(conversationId, message) }
        } ?: return null
        scope.launch { streamer.stream(turn.replyId, CannedReplies.next(repliesSoFar = turn.repliesBefore)) }
        return turn.conversationId
    }

    /** Saves the user's message and a thinking reply, creating the conversation when there is none. */
    private suspend fun startTurn(conversationId: Long?, text: String): Turn? {
        val now = clock.now().toEpochMilliseconds()
        val conversation = if (conversationId == null) {
            val created = ConversationEntity(
                title = ConversationTitles.from(text),
                modelId = ModelCatalogue.default.id,
                createdAt = now,
                updatedAt = now,
            )
            created.copy(id = conversationDao.insert(created))
        } else {
            val existing = conversationDao.get(conversationId)
            if (existing == null || messageDao.hasUnfinishedReply(conversationId)) return null
            conversationDao.setUpdatedAt(conversationId, now)
            existing
        }
        val repliesBefore = messageDao.countReplies(conversation.id)
        messageDao.insert(
            MessageEntity(
                conversationId = conversation.id,
                role = MessageRole.USER,
                text = text,
                blocks = emptyList(),
                modelId = null,
                status = MessageStatus.DONE,
                latencyMillis = null,
                createdAt = now,
            ),
        )
        val replyId = messageDao.insert(
            MessageEntity(
                conversationId = conversation.id,
                role = MessageRole.ASSISTANT,
                text = "",
                blocks = emptyList(),
                modelId = conversation.modelId,
                status = MessageStatus.THINKING,
                latencyMillis = null,
                createdAt = now,
            ),
        )
        return Turn(conversationId = conversation.id, replyId = replyId, repliesBefore = repliesBefore)
    }

    private fun <T> afterRecovery(source: () -> Flow<T>): Flow<T> = flow {
        recovery.await()
        emitAll(source().distinctUntilChanged())
    }

    private class Turn(val conversationId: Long, val replyId: Long, val repliesBefore: Int)
}
