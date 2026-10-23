package br.com.weslleycampos.chat.core.database.daos

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import br.com.weslleycampos.chat.core.database.TransactionRunner
import br.com.weslleycampos.chat.core.database.entities.MessageEntity

/**
 * Forking a conversation at one of its messages: the thread up to that message, the insert of its
 * copies, and the fork banner's dismissal. The fork itself is a [ConversationDao.insert] with its
 * `forkedFromTitle` and `forkedAtMessage` set; run it and [insertMessages] in one
 * [TransactionRunner] block, so a fork never exists without its thread.
 */
@Dao
interface ForkDao {
    /**
     * The conversation's messages up to and including [messageId], in the order they were written.
     * A [messageId] that is not one of the conversation's still bounds the list, so the caller
     * checks that the last message is [messageId].
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND id <= :messageId ORDER BY id")
    suspend fun messagesThrough(conversationId: Long, messageId: Long): List<MessageEntity>

    /**
     * Inserts [messages] in their order, each under the id SQLite assigns (pass `0`), so they read
     * in that order. Fails when one's conversation does not exist.
     */
    @Insert
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * Hides the fork banner of the conversation for good, leaving every other column as it is.
     * Returns how many conversations it changed: `0` for an unknown [id], a conversation that is not
     * a fork, or one whose banner is already dismissed.
     */
    @Query(
        "UPDATE conversations SET forkBannerDismissed = 1 " +
            "WHERE id = :id AND forkedFromTitle IS NOT NULL AND forkBannerDismissed = 0",
    )
    suspend fun dismissBanner(id: Long): Int
}
