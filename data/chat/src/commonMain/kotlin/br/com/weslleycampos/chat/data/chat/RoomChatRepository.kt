package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.CompareDao
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.HistoryDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.daos.containsPattern
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.ConversationTitles
import br.com.weslleycampos.chat.data.chat.models.HistorySearch
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.replies.CannedReplies
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import br.com.weslleycampos.chat.data.chat.replies.StreamedReply
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
 * [conversationDao], one at a time with sends and choices. A choice's compare columns are written
 * through [compareDao], in the transaction that writes its model.
 */
internal class RoomChatRepository(
    private val conversationDao: ConversationDao,
    private val historyDao: HistoryDao,
    private val messageDao: MessageDao,
    private val compareDao: CompareDao,
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
            rows.toMessages(personas)
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
        scope.launch { streamer.stream(turn.replies()) }
        return turn.conversationId
    }

    override suspend fun choose(conversationId: Long, choice: ModelChoice): Boolean {
        val stored = models.stored(choice) ?: return false
        return sending.withLock {
            transactions.inWriteTransaction {
                val found = conversationDao.setModel(conversationId, stored.modelId, stored.personaId) > 0
                if (found) compareDao.setCompare(conversationId, stored.compareOn, stored.compareModelId)
                found
            }
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
 * top and back from the archive), the user's message and a thinking reply, or the two thinking
 * sides of a compare pair.
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
            compareOn = choice.compareOn,
            compareModelId = choice.compareModelId,
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

    /**
     * Saves the user's message and its thinking reply: from the conversation's model and persona, or,
     * while it compares its model with a second one, a pair of sides with no persona, the
     * conversation's model first.
     */
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
        val reply = thinking(conversation.id, createdAt = now)
        val second = conversation.comparedModelId()
        val replyIds = if (second == null) {
            listOf(messageDao.insert(reply.copy(modelId = conversation.modelId, personaId = conversation.personaId)))
        } else {
            listOf(
                messageDao.insert(reply.copy(modelId = conversation.modelId, pairSide = PAIR_FIRST_SIDE)),
                messageDao.insert(reply.copy(modelId = second, pairSide = PAIR_SECOND_SIDE)),
            )
        }
        return Turn(conversationId = conversation.id, replyIds = replyIds, repliesBefore = repliesBefore)
    }

    /** A thinking reply in the conversation, before its model, persona and pair side are set. */
    private fun thinking(conversationId: Long, createdAt: Long): MessageEntity = MessageEntity(
        conversationId = conversationId,
        role = MessageRole.ASSISTANT,
        text = "",
        blocks = emptyList(),
        modelId = null,
        status = MessageStatus.THINKING,
        latencyMillis = null,
        createdAt = createdAt,
    )
}

/**
 * The model the conversation's next reply is compared with: its second model while compare is on,
 * no persona answers, and the catalogue still has that model, which is not the conversation's own.
 * `null` for a single reply.
 */
private fun ConversationEntity.comparedModelId(): String? = compareModelId?.takeIf {
    compareOn && personaId == null && it != modelId && ModelCatalogue.find(it) != null
}

/**
 * A send's rows: [replyIds] holds its reply, or a compare pair's two sides in their order, which
 * play the canned reply that comes after [repliesBefore] replies and its alternative.
 */
private class Turn(val conversationId: Long, val replyIds: List<Long>, val repliesBefore: Int) {
    fun replies(): List<StreamedReply> = replyIds.mapIndexed { side, id ->
        val blocks = if (side == PAIR_FIRST_SIDE) {
            CannedReplies.next(repliesSoFar = repliesBefore)
        } else {
            CannedReplies.alternative(repliesSoFar = repliesBefore)
        }
        StreamedReply(replyId = id, blocks = blocks)
    }
}

/** [source] without repeats, once [recovery] has marked the replies an earlier run left playing. */
private fun <T> afterRecovery(recovery: Deferred<*>, source: () -> Flow<T>): Flow<T> = flow {
    recovery.await()
    emitAll(source().distinctUntilChanged())
}

/** A [ModelChoice] as the `modelId`, `personaId`, `compareOn` and `compareModelId` columns store it. */
private class StoredChoice(
    val modelId: String,
    val personaId: String? = null,
    val compareOn: Boolean = false,
    val compareModelId: String? = null,
)

/**
 * [choice] as the columns store it: a persona with the model its stored row names and compare off,
 * a model on its own or compared with a second one, or the default model with no persona and compare
 * off for `null`. `null` for a model the catalogue does not have or a persona that is not stored.
 */
private suspend fun ModelRepository.stored(choice: ModelChoice?): StoredChoice? {
    if (choice == null) return StoredChoice(modelId = observeDefaultModel().first().id)
    val persona = choice.persona ?: return choice.storedModel()
    val stored = observePersonas().first().firstOrNull { it.id == persona.id } ?: return null
    return StoredChoice(modelId = stored.model.id, personaId = stored.id)
}

/**
 * A choice with no persona as the columns store it: its model, and with compare on the second
 * model, which turning compare off drops. `null` for a model the catalogue does not have, or a
 * second model that is not in it or is the first one.
 */
private fun ModelChoice.storedModel(): StoredChoice? {
    val first = ModelCatalogue.find(model.id) ?: return null
    if (!compareOn) return StoredChoice(modelId = first.id)
    val second = compareWith ?: return StoredChoice(modelId = first.id, compareOn = true)
    if (second.id == first.id || ModelCatalogue.find(second.id) == null) return null
    return StoredChoice(modelId = first.id, compareOn = true, compareModelId = second.id)
}

private fun ModelRepository.personasById(): Flow<Map<String, Persona>> =
    observePersonas().map { personas -> personas.associateBy(Persona::id) }
