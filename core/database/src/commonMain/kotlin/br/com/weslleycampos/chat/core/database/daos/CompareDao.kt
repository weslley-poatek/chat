package br.com.weslleycampos.chat.core.database.daos

import androidx.room3.Dao
import androidx.room3.Query
import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.entities.MessageEntity

/**
 * Compare mode's queries: a conversation's compare choice, and the two sides of a compare pair.
 *
 * A pair is two assistant rows of `messages` that one send writes one right after the other, first
 * side first ([MessageEntity.pairSide]). They stream like any reply, through [MessageDao]. Set a
 * conversation's model with [ConversationDao.setModel] and its compare choice with [setCompare] in
 * one [TransactionRunner] block, so its observers never read half of a choice.
 */
@Dao
interface CompareDao {
    /**
     * Turns compare mode on or off for the conversation, with the catalogue id of the model it is
     * compared with, or `null` for none, leaving `updatedAt` and every other column as they are.
     * Returns how many conversations it changed: `0` for an unknown [id].
     */
    @Query("UPDATE conversations SET compareOn = :compareOn, compareModelId = :compareModelId WHERE id = :id")
    suspend fun setCompare(id: Long, compareOn: Boolean, compareModelId: String?): Int

    /**
     * The conversation's first two messages from [firstSideId] on, in the order they were written:
     * the two sides of a compare pair when [firstSideId] is the row of a pair's first side. Any other
     * id reads whatever rows follow it, so the caller checks they are a first and a second side.
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND id >= :firstSideId ORDER BY id LIMIT 2")
    suspend fun pairFrom(conversationId: Long, firstSideId: Long): List<MessageEntity>
}
