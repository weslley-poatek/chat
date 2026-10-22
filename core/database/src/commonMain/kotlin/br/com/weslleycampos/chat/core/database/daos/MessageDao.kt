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

    /** Rewrites a reply's blocks, status and latency, leaving its other columns as they are. */
    @Update(entity = MessageEntity::class)
    suspend fun updateReply(progress: ReplyProgress)

    /** The conversation's assistant messages, finished or not. */
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND role = 'assistant'")
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
