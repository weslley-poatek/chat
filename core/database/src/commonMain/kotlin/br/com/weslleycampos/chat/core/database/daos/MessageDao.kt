package br.com.weslleycampos.chat.core.database.daos

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.ReplyProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    /** The conversation's messages in the order they were written. Emits again on every change. */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    /** Returns the id SQLite assigned. Fails when [MessageEntity.conversationId] has no conversation. */
    @Insert
    suspend fun insert(message: MessageEntity): Long

    /**
     * Rewrites a reply's blocks, status and latency, leaving its other columns as they are. Returns
     * how many replies it changed: `0` once the reply is gone with its deleted conversation.
     */
    @Update(entity = MessageEntity::class)
    suspend fun updateReply(progress: ReplyProgress): Int

    /**
     * [updateReply] for several replies in one transaction, as a compare pair's two sides on one
     * tick, so observers read both changes at once. Returns how many replies it changed: `0` once
     * they are gone with their deleted conversation.
     */
    @Update(entity = MessageEntity::class)
    suspend fun updateReplies(progress: List<ReplyProgress>): Int

    /**
     * The conversation's replies, finished or not. A compare pair counts once, as the one reply to
     * the message it answers: only its first side is counted.
     */
    @Query(
        "SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND role = 'assistant' " +
            "AND (pairSide IS NULL OR pairSide = 0)",
    )
    suspend fun countReplies(conversationId: Long): Int

    /** Whether a reply in the conversation is still thinking or streaming. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM messages WHERE conversationId = :conversationId " +
            "AND status IN ('thinking', 'streaming'))",
    )
    suspend fun hasUnfinishedReply(conversationId: Long): Boolean

    /** Marks one reply interrupted, keeping the blocks it revealed. */
    @Query("UPDATE messages SET status = 'interrupted' WHERE id = :id AND status IN ('thinking', 'streaming')")
    suspend fun interruptReply(id: Long)

    /** Marks every reply still thinking or streaming interrupted, and returns how many there were. */
    @Query("UPDATE messages SET status = 'interrupted' WHERE status IN ('thinking', 'streaming')")
    suspend fun interruptUnfinishedReplies(): Int
}
