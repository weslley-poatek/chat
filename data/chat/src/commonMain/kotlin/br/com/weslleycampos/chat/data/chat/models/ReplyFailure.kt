package br.com.weslleycampos.chat.data.chat.models

/**
 * Why a reply stopped before it finished: the reason a toast gives. The reply itself reads
 * [ReplyStatus.INTERRUPTED], with what it showed until then; the reason is not stored.
 */
enum class ReplyFailure {
    /** No Fuelix key is stored: Settings' "api keys" has none. */
    NO_KEY,

    /** Fuelix refused the stored key. */
    KEY_REFUSED,

    /** The key may not use the model, or Fuelix has no such model. */
    MODEL_UNAVAILABLE,

    /** Fuelix is rate limiting the key for now. */
    RATE_LIMITED,

    /** Fuelix could not be reached, or the connection broke on the way. */
    UNREACHABLE,

    /** Fuelix could not answer: an error, an answer it should not send, or an empty reply. */
    FAILED,
}

/** A reply in conversation [conversationId] stopped for [failure]: one event, as it stops. */
data class ReplyFailed(val conversationId: Long, val failure: ReplyFailure)
