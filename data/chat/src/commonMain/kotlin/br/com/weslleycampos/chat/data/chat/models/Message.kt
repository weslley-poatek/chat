package br.com.weslleycampos.chat.data.chat.models

import kotlin.time.Duration
import kotlin.time.Instant

/** One message of a conversation: what the user sent, or the reply to it, one model's or a compare pair. */
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

    /**
     * A compare reply: two models answering the same message side by side, the prototype's `pair`.
     * It is one message, in the place a [Assistant] would take, and it has no persona: compare and
     * personas never mix. Its sides stream at once, each on its own, and a new message waits for
     * both ([isInProgress]).
     *
     * @property id Its first side's [CompareSide.id]; "continue with this →" names the pair by it
     *   (`CompareRepository.continueWith`).
     * @property sides Always two: `[0]` the conversation's model, with the prototype's `REPLIES`, and
     *   `[1]` the model it was compared with, with `REPLY_ALT`: the left and right cards.
     * @property createdAt When the message it answers was sent.
     */
    data class ComparePair(
        override val id: Long,
        override val conversationId: Long,
        val sides: List<CompareSide>,
        override val createdAt: Instant,
    ) : Message {
        /** A side is still thinking or streaming, so the conversation accepts no new message yet. */
        val isInProgress: Boolean
            get() = sides.any { it.status.isInProgress }
    }
}

/**
 * One card of a [Message.ComparePair]: a reply like [Message.Assistant]'s, from its own [model].
 *
 * @property id Unique among the conversation's messages and sides, for keys such as a code block's.
 * @property model The model that wrote it: the card's header and caret colour.
 * @property blocks They grow while it streams; see [ReplyStatus].
 * @property status Its own: one side can be done while the other still streams. Only a
 *   [ReplyStatus.DONE] side offers "continue with this →".
 * @property latency How long it streamed, from the end of the pair's thinking to its last block.
 *   `null` until [status] is [ReplyStatus.DONE]. The prototype's pair meta shows none.
 */
data class CompareSide(
    val id: Long,
    val model: ChatModel,
    val blocks: List<Block>,
    val status: ReplyStatus,
    val latency: Duration?,
)

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
