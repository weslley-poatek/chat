package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import kotlinx.coroutines.flow.Flow

/**
 * Conversations and their messages, kept on this device. Every read is a [Flow] over the database
 * that emits the current value when collected and again on every change that alters it; nothing
 * emits twice in a row with the same value.
 *
 * Replies are canned and play in the data layer: [send] saves the user's message and a reply that
 * starts [ReplyStatus.THINKING], then streams the reply into the database. The UI only observes.
 *
 * A new chat has no conversation and nothing in the database: show its empty state on
 * [ModelRepository.observeDefaultModel] (or what the model picker picked since) with
 * [observeRecentConversations], and pass a `null` conversation id and that [ModelChoice] to [send],
 * which creates the conversation.
 */
interface ChatRepository {
    /**
     * The conversation, with the model and persona that answer its next message, or `null` when
     * there is none with this [id].
     */
    fun observeConversation(id: Long): Flow<Conversation?>

    /**
     * The conversation's messages, oldest first: each [Message.User] followed by its
     * [Message.Assistant]. Emits on every tick of a reply that is streaming, about every 28 ms.
     * Empty for an id with no conversation.
     */
    fun observeMessages(conversationId: Long): Flow<List<Message>>

    /**
     * The [RECENT_CONVERSATIONS] most recently updated conversations, newest first: the empty
     * state's "recent" list. A conversation moves to the top when a message is sent in it.
     */
    fun observeRecentConversations(): Flow<List<Conversation>>

    /**
     * Sends [text], trimmed, and starts its reply, then returns the conversation's id without
     * waiting for the reply.
     *
     * With a `null` [conversationId] it first creates a conversation, titled after [text], on
     * [newChat]: the model and persona the picker picked before this first send, or the default
     * model with no persona when it is `null`. Observe the returned id from then on. A conversation
     * that exists answers with its own model and persona and ignores [newChat]; [choose] changes
     * them. The reply records the model and persona it is written with.
     *
     * Sends nothing and returns `null` when [text] is blank, when [conversationId] names no
     * conversation, when [newChat]'s model is not in the catalogue or its persona is not stored, or
     * while the conversation's last reply [ReplyStatus.isInProgress], so replies never overlap in
     * one conversation.
     *
     * The reply thinks for 320–619 ms, then streams one tick every 28 ms until it is
     * [ReplyStatus.DONE] with its latency. It keeps playing, and is saved, after the UI that sent
     * it is gone; one that was still playing when the app was closed reads as
     * [ReplyStatus.INTERRUPTED] the next time the app starts.
     */
    suspend fun send(conversationId: Long?, text: String, newChat: ModelChoice? = null): Long?

    /**
     * Makes [choice] answer the conversation's next message, as a pick in the model picker does: a
     * model on its own drops the persona, and a persona answers with its own model. The conversation
     * keeps its place in "recent", and a reply already on its way keeps the model and persona it
     * started with. Returns `false`, changing nothing, when [conversationId] names no conversation,
     * the model is not in the catalogue or the persona is not stored.
     */
    suspend fun choose(conversationId: Long, choice: ModelChoice): Boolean

    companion object {
        /** The prototype's empty state lists the first three. */
        const val RECENT_CONVERSATIONS = 3
    }
}
