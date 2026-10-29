package br.com.weslleycampos.chat.data.chat.models

/**
 * Voice mode on one conversation, or on a new one before its first exchange is saved: what the
 * Voice screen's model chip and accent show.
 *
 * @property conversationId The conversation the session saves into, or `null` for a new session,
 *   until its first transcript creates the conversation.
 * @property model The model that answers the next exchange: the chip's short name and the accent.
 *   A new session's is the default model.
 * @property persona The persona that answers with [model], or `null` for the model on its own. A new
 *   session has none: the default is a model only.
 */
data class VoiceSession(
    val conversationId: Long?,
    val model: ChatModel,
    val persona: Persona? = null,
) {
    /** [model] and [persona], as the model picker marks them. Voice mode never compares. */
    val choice: ModelChoice
        get() = ModelChoice(model = model, persona = persona)
}

/**
 * One exchange of voice mode: what the device heard the user say, and what the model says back, its
 * reply from Fuelix.
 *
 * @property transcript The user's words as the device heard them, shown as they come while
 *   listening and whole while thinking. Saved as the user's message when listening ends.
 * @property reply The model's words, revealed word by word as the device speaks them: empty until
 *   `VoiceRepository.answer` has them, saved as the reply as thinking ends.
 */
data class VoiceExchange(val transcript: String, val reply: String = "")

/** What `VoiceRepository.answer` got for a turn. */
sealed interface VoiceAnswer {
    /** The model's [reply], saved, its words separated by single spaces for speaking. */
    data class Spoken(val reply: String) : VoiceAnswer

    /** No reply, for [failure]: the transcript stays with none. */
    data class Failed(val failure: ReplyFailure) : VoiceAnswer

    /** The turn needs no reply any more: its conversation is gone, or its transcript was answered or followed. */
    data object Dropped : VoiceAnswer
}

/**
 * A transcript that `VoiceRepository.saveTranscript` saved, waiting for its reply.
 *
 * @property conversationId The conversation it was saved in: the new one when it created it.
 * @property transcriptId The id of the user's message it was saved as.
 * @property exchange The exchange it belongs to: thinking shows its transcript, and
 *   `VoiceRepository.answer` gets its reply for speaking to reveal.
 */
data class VoiceTurn(val conversationId: Long, val transcriptId: Long, val exchange: VoiceExchange)
