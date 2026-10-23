package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.daos.ForkDao
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import kotlin.time.Clock

/**
 * [ForkRepository] on Room. A fork is one write transaction, so it never exists without its thread,
 * and it needs no lock with [RoomChatRepository]'s sends: it copies only messages up to the one it
 * is given, which a later send never changes, and refuses a reply still being written.
 *
 * A compare pair is copied whole, both its sides, when the fork is made at a message after it, and
 * counts as one message in the fork's position. A fork at a pair, at either side's row, is refused:
 * a pair has no action row, and ending a fork on one side would leave it without its partner.
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
        val last = thread.lastOrNull()
        if (last?.id != messageId || last.pairSide != null) return null
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
                forkedAtMessage = thread.count { it.pairSide != PAIR_SECOND_SIDE },
                compareOn = source.compareOn,
                compareModelId = source.compareModelId,
            ),
        )
        forkDao.insertMessages(thread.map { it.copy(id = 0, conversationId = forkId) })
        return forkId
    }
}
