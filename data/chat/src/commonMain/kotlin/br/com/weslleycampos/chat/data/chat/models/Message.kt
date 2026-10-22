package br.com.weslleycampos.chat.data.chat.models

import kotlin.time.Duration
import kotlin.time.Instant

/** One message of a conversation: what the user sent, or the reply to it. */
sealed interface Message {
    val id: Long
    val conversationId: Long
    val createdAt: Instant

    /** What the user sent, trimmed. */
    data class User(
        override val id: Long,
        override val conversationId: Long,
        val text: String,
        override val createdAt: Instant,
    ) : Message

    /**
     * A canned reply. [blocks] grow while it streams; see [ReplyStatus].
     *
     * @property model The model that wrote it, for the meta row's short name.
     * @property persona The persona it was written as, for the meta row's " · code reviewer", or
     *   `null` when the model answered on its own. Changing the conversation's persona later does
     *   not change it.
     * @property latency How long it streamed, from its first block to its last: the meta row's
     *   "0.8s". `null` until [status] is [ReplyStatus.DONE].
     * @property createdAt When the message it answers was sent.
     */
    data class Assistant(
        override val id: Long,
        override val conversationId: Long,
        val model: ChatModel,
        val persona: Persona? = null,
        val blocks: List<Block>,
        val status: ReplyStatus,
        val latency: Duration?,
        override val createdAt: Instant,
    ) : Message
}

/** Where a reply is: [THINKING] → [STREAMING] → [DONE], or [INTERRUPTED] if it stopped on the way. */
enum class ReplyStatus {
    /** Sent and waiting: no blocks yet. The prototype's `thinking`, a lone blinking caret. */
    THINKING,

    /** Revealing its blocks; the last one ends with the caret. */
    STREAMING,

    /** Every block revealed, and the latency known. */
    DONE,

    /**
     * Stopped before it finished: the app was closed or its stream failed. Its blocks are what it
     * revealed until then, possibly none. It never resumes.
     */
    INTERRUPTED,
    ;

    /** While true, the conversation accepts no new message: the prototype's `streaming`. */
    val isInProgress: Boolean
        get() = this == THINKING || this == STREAMING
}
