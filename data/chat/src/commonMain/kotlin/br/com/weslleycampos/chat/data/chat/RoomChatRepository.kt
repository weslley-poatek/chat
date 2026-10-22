package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.HistoryDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.daos.containsPattern
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.ConversationTitles
import br.com.weslleycampos.chat.data.chat.models.HistorySearch
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.replies.CannedReplies
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
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
 * Personas and the default model come from [models], so a persona read here is one that
 * [ModelRepository.observePersonas] lists. Replies stream in [scope], which outlives any screen.
 * History's lists come from [historyDao]; pinning, archiving and deleting go through
 * [conversationDao], one at a time with sends and choices.
 */
internal class RoomChatRepository(
    private val conversationDao: ConversationDao,
    private val historyDao: HistoryDao,
    private val messageDao: MessageDao,
    private val transactions: TransactionRunner,
    private val models: ModelRepository,
    private val streamer: ReplyStreamer,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : ChatRepository {
    private val recovery: Deferred<Int> = scope.async { messageDao.interruptUnfinishedReplies() }

    /**
     * Serialises sends, choices, pins, archives and deletes, so two sends to one conversation cannot
     * both see it idle, and a delete cannot land between a send's check and its writes.
     */
    private val sending = Mutex()

    private val turns = TurnWriter(conversationDao = conversationDao, messageDao = messageDao, clock = clock)

    override fun observeConversation(id: Long): Flow<Conversation?> = afterRecovery(recovery) {
        combine(conversationDao.observe(id), models.personasById()) { row, personas -> row?.toConversation(personas) }
    }

    override fun observeMessages(conversationId: Long): Flow<List<Message>> = afterRecovery(recovery) {
        combine(messageDao.observeByConversation(conversationId), models.personasById()) { rows, personas ->
            rows.map { it.toMessage(personas) }
        }
    }

    override fun observeRecentConversations(): Flow<List<Conversation>> = afterRecovery(recovery) {
        val recent = conversationDao.observeRecent(ChatRepository.RECENT_CONVERSATIONS)
        combine(recent, models.personasById()) { rows, personas -> rows.map { it.toConversation(personas) } }
    }

    override fun observeHistory(query: String): Flow<List<Conversation>> = afterRecovery(recovery) {
        val text = HistorySearch.text(query)
        val rows = if (text == null) {
            historyDao.observeAll()
        } else {
            historyDao.search(pattern = containsPattern(text), modelIds = HistorySearch.modelIds(text))
        }
        combine(rows, models.personasById()) { conversations, personas ->
            conversations.map { it.toConversation(personas) }
        }
    }

    override fun observeConversationCount(): Flow<Int> = afterRecovery(recovery) { historyDao.observeCount() }

    override suspend fun send(conversationId: Long?, text: String, newChat: ModelChoice?): Long? {
        val message = text.trim()
        if (message.isEmpty()) return null
        recovery.await()
        val turn = sending.withLock {
            if (conversationId == null) {
                val choice = models.stored(newChat) ?: return null
                transactions.inWriteTransaction { turns.startNew(message, choice) }
            } else {
                transactions.inWriteTransaction { turns.continueIn(conversationId, message) }
            }
        } ?: return null
        scope.launch { streamer.stream(turn.replyId, CannedReplies.next(repliesSoFar = turn.repliesBefore)) }
        return turn.conversationId
    }

    override suspend fun choose(conversationId: Long, choice: ModelChoice): Boolean {
        val stored = models.stored(choice) ?: return false
        return sending.withLock {
            conversationDao.setModel(conversationId, modelId = stored.modelId, personaId = stored.personaId) > 0
        }
    }

    override suspend fun setPinned(conversationId: Long, pinned: Boolean): Boolean =
        sending.withLock { conversationDao.setPinned(conversationId, pinned) > 0 }

    override suspend fun archive(conversationId: Long): Boolean =
        sending.withLock { conversationDao.setArchived(conversationId, archived = true) > 0 }

    /** A reply still streaming into it finds its row gone on its next write, and stops: see [ReplyStreamer]. */
    override suspend fun delete(conversationId: Long): Boolean =
        sending.withLock { conversationDao.delete(conversationId) > 0 }
}

/**
 * A send's writes, run inside the caller's transaction: the conversation (created, or moved to the
 * top and back from the archive), the user's message and a thinking reply.
 */
private class TurnWriter(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val clock: Clock,
) {
    /** A new conversation titled after [text], answered by [choice], and its first turn. */
    suspend fun startNew(text: String, choice: StoredChoice): Turn = startTurn(create(text, choice), text)

    /** The conversation's next turn, or `null` when it is gone or its reply is on its way. */
    suspend fun continueIn(conversationId: Long, text: String): Turn? =
        continued(conversationId)?.let { startTurn(it, text) }

    /** A new conversation titled after [text], answered by [choice]. */
    private suspend fun create(text: String, choice: StoredChoice): ConversationEntity {
        val now = clock.now().toEpochMilliseconds()
        val created = ConversationEntity(
            title = ConversationTitles.from(text),
            modelId = choice.modelId,
            personaId = choice.personaId,
            createdAt = now,
            updatedAt = now,
        )
        return created.copy(id = conversationDao.insert(created))
    }

    /**
     * The conversation, moved to the top of "recent" and History and back from the archive, or
     * `null` when it is gone or its reply is on its way.
     */
    private suspend fun continued(conversationId: Long): ConversationEntity? {
        val existing = conversationDao.get(conversationId)
        if (existing == null || messageDao.hasUnfinishedReply(conversationId)) return null
        conversationDao.setUpdatedAt(conversationId, clock.now().toEpochMilliseconds())
        if (existing.archived) conversationDao.setArchived(conversationId, archived = false)
        return existing
    }

    /** Saves the user's message and a thinking reply from the conversation's model and persona. */
    private suspend fun startTurn(conversation: ConversationEntity, text: String): Turn {
        val now = clock.now().toEpochMilliseconds()
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
                personaId = conversation.personaId,
            ),
        )
        return Turn(conversationId = conversation.id, replyId = replyId, repliesBefore = repliesBefore)
    }
}

private class Turn(val conversationId: Long, val replyId: Long, val repliesBefore: Int)

/** [source] without repeats, once [recovery] has marked the replies an earlier run left playing. */
private fun <T> afterRecovery(recovery: Deferred<*>, source: () -> Flow<T>): Flow<T> = flow {
    recovery.await()
    emitAll(source().distinctUntilChanged())
}

/** A [ModelChoice] as the `modelId` and `personaId` columns store it. */
private class StoredChoice(val modelId: String, val personaId: String?)

/**
 * [choice] as the columns store it: a persona with the model its stored row names, a model on its
 * own, or the default model with no persona for `null`. `null` for a model the catalogue does not
 * have or a persona that is not stored.
 */
private suspend fun ModelRepository.stored(choice: ModelChoice?): StoredChoice? {
    if (choice == null) return StoredChoice(modelId = observeDefaultModel().first().id, personaId = null)
    val persona = choice.persona
        ?: return ModelCatalogue.find(choice.model.id)?.let { StoredChoice(modelId = it.id, personaId = null) }
    val stored = observePersonas().first().firstOrNull { it.id == persona.id } ?: return null
    return StoredChoice(modelId = stored.model.id, personaId = stored.id)
}

private fun ModelRepository.personasById(): Flow<Map<String, Persona>> =
    observePersonas().map { personas -> personas.associateBy(Persona::id) }
