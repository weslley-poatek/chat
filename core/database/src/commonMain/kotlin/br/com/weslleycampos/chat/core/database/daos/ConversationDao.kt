package br.com.weslleycampos.chat.core.database.daos

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    /** The conversation, or `null` while there is none with this id. Emits again on every change. */
    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: Long): Flow<ConversationEntity?>

    /** The [limit] most recently updated conversations, newest first; the later id wins a tie. */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC, id DESC LIMIT :limit")
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
}
