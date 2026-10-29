package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.data.chat.models.VoiceAnswer
import br.com.weslleycampos.chat.data.chat.models.VoiceSession
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import kotlinx.coroutines.flow.Flow

/**
 * Voice mode's sessions, saved like typed conversations: each exchange is the user's message and
 * its reply in Room, so a voice conversation is listed in History ([ChatRepository.observeHistory])
 * and opens as text ([ChatRepository.observeConversation], [ChatRepository.observeMessages]) like
 * any other, and a typed conversation can go on in voice mode.
 *
 * The Voice screen listens with the device's speech recognition and speaks with its speech
 * synthesis; the replies are Fuelix's. It goes through the design's phases (listening, thinking,
 * speaking, then listening again) and tells this repository when one ends:
 *
 * 1. when listening ends, [saveTranscript] saves what the user said as the user's message, creating
 *    the conversation on the first one, and returns the [VoiceTurn];
 * 2. thinking lasts while [answer] gets that turn's reply from Fuelix and saves it, whole and done,
 *    before speaking reveals it.
 *
 * A session that ends while listening saves nothing; one that ends while thinking keeps the
 * transcript with no reply; one that ends while speaking keeps both, the reply whole. Nothing is
 * ever left thinking or streaming.
 *
 * Voice mode never compares: a reply is always one [Message.Assistant] from the conversation's
 * model, even while the conversation compares in text.
 */
interface VoiceRepository {
    /**
     * The session on the conversation, or on a new one for a `null` [conversationId].
     *
     * A new session is on the default model ([ModelRepository.observeDefaultModel]), with no
     * persona; it emits again when the default model changes.
     * The model picked for it before its first exchange may have a persona: keep that choice and
     * pass it to [saveTranscript] as `newSession`, as a new chat does.
     *
     * A conversation's session carries its model and persona. It emits again when its model or
     * persona changes. `null` when [conversationId] names no conversation, never created or deleted
     * since.
     */
    fun observeSession(conversationId: Long?): Flow<VoiceSession?>

    /**
     * Saves [transcript], what the user was heard saying, trimmed, as the user's message, as
     * listening ends, and returns the turn to [answer].
     *
     * With a `null` [conversationId] it first creates a conversation titled after the transcript,
     * as a first send does, on [newSession]: the model or persona the picker picked before this first
     * exchange, or the default model with no persona when it is `null`. Its compare is dropped:
     * voice mode never compares. Observe [VoiceTurn.conversationId] from then on. A conversation
     * that exists ignores [newSession]; it moves to the top of History and comes back from the
     * archive, as a send does.
     *
     * Saves nothing and returns `null` for a blank [transcript], when [conversationId] names no
     * conversation, when [newSession]'s persona is not stored, or while a typed reply in the
     * conversation [ReplyStatus.isInProgress]: replies never overlap in one conversation.
     */
    suspend fun saveTranscript(conversationId: Long?, transcript: String, newSession: ModelChoice? = null): VoiceTurn?

    /**
     * Gets the reply to [turn]'s transcript from Fuelix and saves it, as thinking ends and speaking
     * starts. The conversation's model reads the conversation so far after a prompt that asks for a
     * short spoken answer, and its persona's prompt while one answers. The reply is saved as one
     * [Message.Assistant] that is [ReplyStatus.DONE] at once, whole, with the model and persona that
     * wrote it, the conversation's as it was asked, the time its transcript was sent, and as its
     * latency the time since that transcript was saved. It does not move the
     * conversation in History.
     *
     * Returns [VoiceAnswer.Spoken] with the reply; [VoiceAnswer.Failed] with the [ReplyFailure] when
     * Fuelix gave none, saving nothing; and [VoiceAnswer.Dropped], saving nothing, when the
     * conversation is gone, or when the transcript is not its last message any more, before or after
     * the request: already answered, or followed by a typed message. So a turn is answered once,
     * however often this is called.
     */
    suspend fun answer(turn: VoiceTurn): VoiceAnswer

    /**
     * The model picker's pick on the Voice screen: makes [choice] answer the conversation's next
     * exchange, with [ChatRepository.choose]'s rules, and makes its model the default for new
     * sessions and new chats ([ModelRepository.setDefaultModel]), since Settings has no default
     * model any more. A persona's own model becomes the default. Compare is dropped: the
     * conversation answers with [choice]'s model alone from then on, in text too.
     *
     * With a `null` [conversationId], before a new session's first exchange, only the default
     * changes; [observeSession] then emits the new model. The default is a model only, so keep a
     * persona [choice] and pass it to [saveTranscript] as `newSession`.
     *
     * Changes nothing and returns `false` when [choice]'s model is not in the catalogue, its persona
     * is not stored, or [conversationId] names no conversation. [Conversation.model] and
     * [Conversation.persona] say what it changed.
     */
    suspend fun choose(conversationId: Long?, choice: ModelChoice): Boolean
}
