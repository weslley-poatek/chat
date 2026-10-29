package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.CompareDao
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.HistoryDao
import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.daos.containsPattern
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.HistorySearch
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.ReplyFailed
import br.com.weslleycampos.chat.data.chat.replies.ReplyPrompts
import br.com.weslleycampos.chat.data.chat.replies.ReplyStreamer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
 * [ModelRepository.observePersonas] lists. Replies stream from Fuelix through [streamer] in [scope],
 * which outlives any screen, and a failure is told on [replyFailures].
 * History's lists come from [historyDao]; pinning, archiving and deleting go through
 * [conversationDao], one at a time with sends and choices. A choice's compare columns are written
 * through [compareDao], in the transaction that writes its model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
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

    /** Failures as they happen; a collector that is not there misses them, and a slow one the oldest. */
    private val failures = MutableSharedFlow<ReplyFailed>(
        extraBufferCapacity = FAILURE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val replyFailures: Flow<ReplyFailed> = failures.asSharedFlow()

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
            models.observeModels().flatMapLatest { listed ->
                historyDao.search(pattern = containsPattern(text), modelIds = HistorySearch.modelIds(text, listed))
            }
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
        // The reply, or both sides of its pair, streams on; a failure is told on replyFailures.
        scope.launch {
            val thread = messageDao.observeByConversation(turn.conversationId).first()
            val personas = models.observePersonas().first().associateBy(Persona::id)
            streamer.stream(ReplyPrompts.requests(turn.replyIds, thread, personas))
                ?.let { failures.tryEmit(ReplyFailed(turn.conversationId, it)) }
        }
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

/** How many failures wait for a slow collector before the oldest is dropped. */
private const val FAILURE_BUFFER = 8

/** [source] without repeats, once [recovery] has marked the replies an earlier run left playing. */
private fun <T> afterRecovery(recovery: Deferred<*>, source: () -> Flow<T>): Flow<T> = flow {
    recovery.await()
    emitAll(source().distinctUntilChanged())
}

private fun ModelRepository.personasById(): Flow<Map<String, Persona>> =
    observePersonas().map { personas -> personas.associateBy(Persona::id) }
