package br.com.weslleycampos.chat.data.chat.models

/**
 * A name and a system prompt that answer with one model: the prototype's `PERSONAS`. Personas are
 * the user's data and live on this device; [PersonaPresets] are the ones every install starts with.
 * There is no provider, so [prompt] changes no reply: the picker shows it, and the chip and reply
 * meta show [name].
 *
 * @property id Stable key, stored with conversations and replies: `reviewer`, `terse`, `digest`.
 * @property name "Code reviewer", as the picker, the model chip and the empty state show it.
 * @property model The model the persona answers with. Picking the persona picks it too.
 * @property prompt "Correctness first, style last. …", the picker row's meta after "→ sonnet-4.5 · ".
 */
data class Persona(
    val id: String,
    val name: String,
    val model: ChatModel,
    val prompt: String,
)
