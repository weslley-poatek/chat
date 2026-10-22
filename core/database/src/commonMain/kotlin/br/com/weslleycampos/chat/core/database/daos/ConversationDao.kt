package br.com.weslleycampos.chat.core.database.daos

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    /**
     * The conversation, or `null` while there is none with this id. Emits again on every change. An
     * archived conversation is still observed: only the lists leave it out.
     */
    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: Long): Flow<ConversationEntity?>

    /**
     * The [limit] most recently updated conversations that are not archived, newest first; the later
     * id wins a tie. History's lists are [HistoryDao]'s.
     */
    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY updatedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    /** Returns the id SQLite assigned. */
    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun setUpdatedAt(id: Long, updatedAt: Long)

    /**
     * Sets the model and persona that answer the conversation's next message, leaving `updatedAt`
     * and every other column as they are. Returns how many conversations it changed: `0` for an
     * unknown [id].
     */
    @Query("UPDATE conversations SET modelId = :modelId, personaId = :personaId WHERE id = :id")
    suspend fun setModel(id: Long, modelId: String, personaId: String?): Int

    /**
     * Pins or unpins the conversation, leaving `updatedAt` and every other column as they are.
     * Returns how many conversations it changed: `0` for an unknown [id] or one already [pinned].
     */
    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id AND pinned != :pinned")
    suspend fun setPinned(id: Long, pinned: Boolean): Int

    /**
     * Archives the conversation or brings it back, leaving `updatedAt` and every other column as
     * they are. Returns how many conversations it changed: `0` for an unknown [id] or one already
     * [archived].
     */
    @Query("UPDATE conversations SET archived = :archived WHERE id = :id AND archived != :archived")
    suspend fun setArchived(id: Long, archived: Boolean): Int

    /**
     * Deletes the conversation, and with it its messages through their foreign key's cascade.
     * Returns how many conversations it deleted: `0` for an unknown [id].
     */
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long): Int
}
