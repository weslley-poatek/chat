package br.com.weslleycampos.chat.data.chat.models

/**
 * Voice mode on one conversation, or on a new one before its first exchange is saved: what the
 * Voice screen's model chip, accent and next listening phase show.
 *
 * @property conversationId The conversation the session saves into, or `null` for a new session,
 *   until its first transcript creates the conversation.
 * @property model The model that answers the next exchange: the chip's short name and the accent.
 *   A new session's is the default model.
 * @property persona The persona that answers with [model], or `null` for the model on its own. A new
 *   session has none: the default is a model only.
 * @property next The exchange the next listening phase plays, picked by how many exchanges the
 *   conversation already has, so a conversation reopened in voice mode goes on where it left off.
 */
data class VoiceSession(
    val conversationId: Long?,
    val model: ChatModel,
    val persona: Persona? = null,
    val next: VoiceExchange,
) {
    /** [model] and [persona], as the model picker marks them. Voice mode never compares. */
    val choice: ModelChoice
        get() = ModelChoice(model = model, persona = persona)
}

/**
 * One canned exchange of voice mode. There is no microphone and no model: what the user is heard
 * saying and what the model says back are the design's lines, in turn.
 *
 * @property transcript The user's words, split on spaces for the word-by-word reveal while
 *   listening and shown whole while thinking. Saved as the user's message when listening ends.
 * @property reply The model's words, revealed word by word while speaking. Saved as the reply, one
 *   paragraph, when thinking ends.
 */
data class VoiceExchange(val transcript: String, val reply: String)

/**
 * A transcript that `VoiceRepository.saveTranscript` saved, waiting for its reply.
 *
 * @property conversationId The conversation it was saved in: the new one when it created it.
 * @property transcriptId The id of the user's message it was saved as.
 * @property exchange The exchange it belongs to: thinking shows its transcript, speaking reveals its
 *   reply, and `VoiceRepository.saveReply` saves that reply.
 */
data class VoiceTurn(val conversationId: Long, val transcriptId: Long, val exchange: VoiceExchange)
