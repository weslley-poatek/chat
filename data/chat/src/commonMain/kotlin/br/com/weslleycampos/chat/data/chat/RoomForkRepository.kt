package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.ForkDao
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import kotlin.time.Clock

/**
 * [ForkRepository] on Room. A fork is one write transaction, so it never exists without its thread,
 * and it needs no lock with [RoomChatRepository]'s sends: it copies only messages up to the one it
 * is given, which a later send never changes, and refuses a reply still being written.
 */
internal class RoomForkRepository(
    private val conversationDao: ConversationDao,
    private val forkDao: ForkDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) : ForkRepository {
    override suspend fun fork(conversationId: Long, messageId: Long): Long? =
        transactions.inWriteTransaction { copy(conversationId, messageId) }

    override suspend fun dismissForkBanner(conversationId: Long): Boolean = forkDao.dismissBanner(conversationId) > 0

    /** The fork's id, or `null` when there is nothing to fork; see [ForkRepository.fork]. */
    private suspend fun copy(conversationId: Long, messageId: Long): Long? {
        val source = conversationDao.get(conversationId) ?: return null
        val thread = forkDao.messagesThrough(conversationId, messageId)
        if (thread.lastOrNull()?.id != messageId) return null
        if (thread.any { it.status.toReplyStatus().isInProgress }) return null
        val now = clock.now().toEpochMilliseconds()
        val forkId = conversationDao.insert(
            ConversationEntity(
                title = source.title,
                modelId = source.modelId,
                personaId = source.personaId,
                createdAt = now,
                updatedAt = now,
                forkedFromTitle = source.title,
                forkedAtMessage = thread.size,
            ),
        )
        forkDao.insertMessages(thread.map { it.copy(id = 0, conversationId = forkId) })
        return forkId
    }
}
