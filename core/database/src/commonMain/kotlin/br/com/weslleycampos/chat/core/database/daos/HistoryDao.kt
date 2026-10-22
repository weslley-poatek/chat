package br.com.weslleycampos.chat.core.database.daos

import androidx.room3.Dao
import androidx.room3.Query
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import kotlinx.coroutines.flow.Flow

/**
 * The lists of `conversations` History shows. [ConversationDao] pins, archives and deletes them.
 * None of them has an archived conversation.
 */
@Dao
interface HistoryDao {
    /**
     * Every conversation that is not archived, newest first, the later id winning a tie: History
     * without a search. Pinned ones are in that order too; grouping them is the caller's. It reads
     * only `conversations`, so a reply streaming into `messages` does not re-run it.
     */
    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    /**
     * The conversations of [observeAll] whose title or one of whose user messages is `LIKE`
     * [pattern], with `\` as its escape character (build it with [containsPattern]), or whose model
     * is one of [modelIds], in the same order. SQLite's `LIKE` ignores the case of ASCII letters
     * only. Replies are not searched. It reads `messages` too, so it re-runs on every write there, a
     * streaming tick included.
     */
    @Query(
        "SELECT * FROM conversations WHERE archived = 0 AND (" +
            "title LIKE :pattern ESCAPE '\\' " +
            "OR modelId IN (:modelIds) " +
            "OR EXISTS(SELECT 1 FROM messages WHERE messages.conversationId = conversations.id " +
            "AND messages.role = 'user' AND messages.text LIKE :pattern ESCAPE '\\')" +
            ") ORDER BY updatedAt DESC, id DESC",
    )
    fun search(pattern: String, modelIds: List<String>): Flow<List<ConversationEntity>>

    /** How many conversations [observeAll] lists: every one that is not archived. */
    @Query("SELECT COUNT(*) FROM conversations WHERE archived = 0")
    fun observeCount(): Flow<Int>
}
