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
 * @property updatedAt When its latest message was sent; "recent" and History sort on it, and
 *   History groups it by its local day. Changing the model or persona, pinning and archiving do not
 *   move it.
 * @property pinned Whether History lists it under "pinned", above the days.
 * @property archived Whether it is archived: History and "recent" leave it out, but it is kept with
 *   its messages and still opens by id. A message sent in it brings it back.
 * @property fork For a conversation made by "⑂ fork here", where it was forked from, for the fork
 *   banner; `null` for every other conversation.
 * @property compareOn Compare mode is on for its next message ([ModelChoice.compareOn]); never while a
 *   [persona] answers.
 * @property compareWith The second model its next message is compared with while [compareOn]
 *   ([ModelChoice.compareWith]), or `null`.
 */
data class Conversation(
    val id: Long,
    val title: String,
    val model: ChatModel,
    val persona: Persona? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val fork: ForkOrigin? = null,
    val compareOn: Boolean = false,
    val compareWith: ChatModel? = null,
) {
    /** [model], [persona] and the compare choice, as the model picker marks them. */
    val choice: ModelChoice
        get() = ModelChoice(model = model, persona = persona, compareOn = compareOn, compareWith = compareWith)

    /**
     * The model its next message is compared with, in a compare pair beside [model], or `null`:
     * [ModelChoice.comparing]. History's row shows it as its extra, "⇄ gpt-5".
     */
    val comparing: ChatModel?
        get() = choice.comparing
}
