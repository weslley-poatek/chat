package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.daos.CompareDao
import br.com.weslleycampos.chat.core.database.daos.ConversationDao
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue

/**
 * [CompareRepository] on Room. Continuing is one write transaction, the model and the compare
 * columns together, so it needs no lock with [RoomChatRepository]'s sends: a send's transaction
 * runs wholly before it, with the pair's choice, or wholly after it, with the side's model alone.
 */
internal class RoomCompareRepository(
    private val conversationDao: ConversationDao,
    private val compareDao: CompareDao,
    private val transactions: TransactionRunner,
) : CompareRepository {
    override suspend fun continueWith(conversationId: Long, pairMessageId: Long, side: Int): Boolean =
        transactions.inWriteTransaction { keep(conversationId, pairMessageId, side) }

    /** Whether it continued with the side; see [CompareRepository.continueWith]. */
    private suspend fun keep(conversationId: Long, pairMessageId: Long, side: Int): Boolean {
        val chosen = side(conversationId, pairMessageId, side)?.takeIf { it.status == MessageStatus.DONE }
        val model = chosen?.modelId?.let(ModelCatalogue::find) ?: return false
        val found = conversationDao.setModel(conversationId, modelId = model.id, personaId = null) > 0
        if (found) compareDao.setCompare(conversationId, compareOn = false, compareModelId = null)
        return found
    }

    /** The row of the pair's [side], or `null` when [pairMessageId] is not a pair's first side. */
    private suspend fun side(conversationId: Long, pairMessageId: Long, side: Int): MessageEntity? {
        val pair = compareDao.pairFrom(conversationId, firstSideId = pairMessageId)
        val first = pair.firstOrNull()
        val isPair = first?.id == pairMessageId && first.pairSide == PAIR_FIRST_SIDE &&
            pair.getOrNull(1)?.pairSide == PAIR_SECOND_SIDE
        return if (isPair) pair.getOrNull(side) else null
    }
}
