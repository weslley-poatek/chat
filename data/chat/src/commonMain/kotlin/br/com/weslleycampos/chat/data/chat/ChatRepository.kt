package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.data.chat.models.groupHistory
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
 *
 * History lists [observeHistory], grouped by [groupHistory], and pins, archives and deletes with
 * [setPinned], [archive] and [delete]; it opens a conversation by id with [observeConversation] and
 * [observeMessages], as "recent" does.
 *
 * A fork ([ForkRepository.fork]) is a conversation like any other here; its [Conversation.fork]
 * carries what the fork banner shows.
 *
 * Compare mode is part of the [ModelChoice]: while a conversation compares its model with a second
 * one ([Conversation.comparing]), [send] answers with a [Message.ComparePair], and
 * [CompareRepository.continueWith] goes on with one side's model.
 *
 * Voice mode ([VoiceRepository]) saves each exchange as the user's message and a reply that is
 * done at once, so a voice conversation is listed, searched, opened and sent in here like a typed
 * one, and a typed one can go on in voice mode.
 */
interface ChatRepository {
    /**
     * The conversation, with the model and persona that answer its next message, or `null` when
     * there is none with this [id]: never created, or deleted since. An archived conversation is
     * still observed, with [Conversation.archived] set.
     */
    fun observeConversation(id: Long): Flow<Conversation?>

    /**
     * The conversation's messages, oldest first: each [Message.User] followed by its
     * [Message.Assistant], or by a [Message.ComparePair] when it was sent while compare was on,
     * except in a fork made at a user message ([ForkRepository.fork]), where that message has no
     * reply and the next send follows it. Emits on every tick of a reply that is streaming, about
     * every 28 ms, a pair's two sides changing in one emission. Empty for an id with no
     * conversation.
     */
    fun observeMessages(conversationId: Long): Flow<List<Message>>

    /**
     * The [RECENT_CONVERSATIONS] most recently updated conversations that are not archived, newest
     * first: the empty state's "recent" list. A conversation moves to the top when a message is
     * sent in it; pinning does not move it, and archiving or deleting one lets the next one in.
     */
    fun observeRecentConversations(): Flow<List<Conversation>>

    /**
     * History's list: every conversation that is not archived, the most recently updated first, the
     * later id on a tie, pinned ones included in that order. Pass it to [groupHistory] for the
     * "pinned", "today", "yesterday" and "earlier" groups.
     *
     * With a [query] that is not blank, only the conversations it matches, in the same order. The
     * query is trimmed, and a conversation matches when one of these contains it:
     * - its title;
     * - the text of one of the user's own messages (replies are canned, the same in every
     *   conversation, so they are not searched);
     * - its model's short name, as the row shows it: "gpt" finds every GPT-5 conversation;
     * - while it compares, the short name of the model it is compared with, the row's "⇄ gpt-5".
     *
     * Letters A–Z match whatever their case; other letters, such as "ç" or "É", only as typed.
     * `%`, `_` and `\` are matched as themselves. Emits again whenever the list changes: a send,
     * a pin, an archive, a delete, a model or compare choice; while a query is set, also a reply that streams
     * into a matching conversation's thread, but only when the matches change.
     */
    fun observeHistory(query: String = ""): Flow<List<Conversation>>

    /** How many conversations [observeHistory] lists without a query: every one that is not archived. */
    fun observeConversationCount(): Flow<Int>

    /**
     * Sends [text], trimmed, and starts its reply, then returns the conversation's id without
     * waiting for the reply.
     *
     * With a `null` [conversationId] it first creates a conversation, titled after [text], on
     * [newChat]: the model, persona and compare choice the picker picked before this first send,
     * or the default model with no persona and compare off when it is `null`. Observe the returned
     * id from then on. A conversation that exists answers with its own choice and ignores
     * [newChat]; [choose] changes it. The reply records the model and persona it is written with.
     *
     * While the conversation compares ([ModelChoice.comparing]), the reply is a
     * [Message.ComparePair]: its first side from the conversation's model with the canned reply a
     * single reply would have, its second from the compared model with that reply's alternative.
     * The pair counts as one reply in the rotation of canned replies.
     *
     * Sends nothing and returns `null` when [text] is blank, when [conversationId] names no
     * conversation, when [newChat]'s model or second model is not in the catalogue, its second model
     * is its model, or its persona is not stored, or while the conversation's last reply, either
     * side of a pair, [ReplyStatus.isInProgress], so replies never overlap in one conversation.
     *
     * The reply thinks for 320–619 ms, then streams one tick every 28 ms until it is
     * [ReplyStatus.DONE] with its latency. It keeps playing, and is saved, after the UI that sent
     * it is gone; one that was still playing when the app was closed reads as
     * [ReplyStatus.INTERRUPTED] the next time the app starts. Archiving its conversation lets it
     * finish; deleting it stops it at its next tick. A pair's sides think once, together, then
     * stream on the same ticks, each finishing on its own with its own latency; closing the app or
     * deleting the conversation stops both.
     *
     * A message sent in an archived conversation brings it back to History and "recent", at the
     * top, as it was pinned or not.
     */
    suspend fun send(conversationId: Long?, text: String, newChat: ModelChoice? = null): Long?

    /**
     * Makes [choice] answer the conversation's next message, as a pick in the model picker does: a
     * model on its own drops the persona, and a persona answers with its own model. Compare is
     * stored with it: [ModelChoice.toggledCompare] and [ModelChoice.picked] apply the picker's
     * rules (a persona turns compare off, turning compare off drops the second model). The
     * conversation keeps its place in "recent", and a reply already on its way keeps the model and
     * persona it started with. Returns `false`, changing nothing, when [conversationId] names no
     * conversation, the model or second model is not in the catalogue, the second model is the
     * model itself, or the persona is not stored.
     */
    suspend fun choose(conversationId: Long, choice: ModelChoice): Boolean

    /**
     * Pins the conversation to History's "pinned" group, or unpins it back to its day, as a row's
     * "pin" / "unpin" does. It keeps its place in "recent" and its `updatedAt`. Returns whether it
     * changed anything: `false` for an unknown conversation or one that already is as [pinned]
     * says.
     */
    suspend fun setPinned(conversationId: Long, pinned: Boolean): Boolean

    /**
     * Archives the conversation, as a row's "archive" does: History, "recent" and
     * [observeConversationCount] leave it out, but it stays on the device with its messages,
     * [observeConversation] still reads it, and a reply on its way finishes. Nothing lists archived
     * conversations; a message sent in it brings it back. Returns whether it changed anything:
     * `false` for an unknown conversation or one already archived.
     */
    suspend fun archive(conversationId: Long): Boolean

    /**
     * Deletes the conversation and all its messages from the device, as a row's "delete" does, with
     * no undo. A reply still on its way stops at its next tick, and its conversation's observers
     * read `null` and an empty thread. Returns whether it deleted anything: `false` for an unknown
     * conversation, one already deleted included.
     */
    suspend fun delete(conversationId: Long): Boolean

    companion object {
        /** The prototype's empty state lists the first three. */
        const val RECENT_CONVERSATIONS = 3
    }
}
