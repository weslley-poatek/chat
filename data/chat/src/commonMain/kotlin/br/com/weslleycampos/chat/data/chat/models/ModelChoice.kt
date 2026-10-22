package br.com.weslleycampos.chat.data.chat.models

/**
 * What answers a conversation: a model on its own, or a persona with the model it answers with. It
 * is the prototype's `modelId` and `personaId` together, and what a row of the model picker picks:
 *
 * - a model row: `ModelChoice(model)`, which drops any persona;
 * - a persona row: `ModelChoice(persona)`, which picks the persona's model too.
 *
 * With a [persona], the repository answers with that persona's own model, whatever [model] says.
 */
data class ModelChoice(
    val model: ChatModel,
    val persona: Persona? = null,
) {
    /** A persona row: the persona, on its model. */
    constructor(persona: Persona) : this(model = persona.model, persona = persona)
}
