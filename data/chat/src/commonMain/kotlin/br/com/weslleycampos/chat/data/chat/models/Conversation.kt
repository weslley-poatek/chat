package br.com.weslleycampos.chat.data.chat.models

import kotlin.time.Instant

/**
 * One chat thread on this device.
 *
 * @property title The first message, cut to [ConversationTitles.MAX_LENGTH] characters on one line.
 * @property model The model that answers its next message. While [persona] is set, it is that
 *   persona's model.
 * @property persona The persona that answers its next message, or `null` for [model] on its own.
 * @property createdAt When its first message was sent.
 * @property updatedAt When its latest message was sent; the "recent" list sorts on it. Changing the
 *   model or persona does not move it.
 */
data class Conversation(
    val id: Long,
    val title: String,
    val model: ChatModel,
    val persona: Persona? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** [model] and [persona] as the model picker marks them. */
    val choice: ModelChoice
        get() = ModelChoice(model = model, persona = persona)
}
