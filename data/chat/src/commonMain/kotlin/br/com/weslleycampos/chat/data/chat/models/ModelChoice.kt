package br.com.weslleycampos.chat.data.chat.models

/**
 * What answers a conversation: a model on its own, a persona with the model it answers with, or a
 * model compared with a second one. It is the prototype's `modelId`, `personaId`, `compareOn` and
 * `compareId` together, and what a row of the model picker picks:
 *
 * - a model row: `ModelChoice(model)`, which drops any persona and turns compare off;
 * - a persona row: `ModelChoice(persona)`, which picks the persona's model too;
 * - with compare on, [picked] and [toggledCompare] apply the picker's compare rules.
 *
 * With a [persona], the repository answers with that persona's own model, whatever [model] says,
 * and compare is off: a persona never answers in a compare pair. With compare off, [compareWith] is
 * dropped.
 *
 * @property compareOn Compare mode is on: the picker's "compare · on". On its own, before a second
 *   model is picked ("pick a second model to run beside sonnet-4.5"), a send is still answered by
 *   [model] alone.
 * @property compareWith The second model, the prototype's `compareId`, which answers beside [model]
 *   in a compare pair while [compareOn]. Never [model] itself.
 */
data class ModelChoice(
    val model: ChatModel,
    val persona: Persona? = null,
    val compareOn: Boolean = false,
    val compareWith: ChatModel? = null,
) {
    /** A persona row: the persona, on its model. */
    constructor(persona: Persona) : this(model = persona.model, persona = persona)

    /**
     * The model a send is compared with, answering beside [model] in a compare pair: [compareWith]
     * while compare is on and no persona answers, else `null` for a single reply. The chip's
     * "sonnet-4.5 ⇄ gpt-5", the composer's "ask sonnet-4.5 and gpt-5…" and the picker's "◐".
     */
    val comparing: ChatModel?
        get() = compareWith?.takeIf { compareOn && persona == null && it.id != model.id }

    /**
     * The picker's compare button, the prototype's `toggleCompare`: compare turns on or off, turning
     * it off drops the second model, and the persona goes either way, its model staying.
     */
    fun toggledCompare(): ModelChoice = if (compareOn) {
        ModelChoice(model = model)
    } else {
        ModelChoice(model = model, compareOn = true, compareWith = compareWith)
    }

    /**
     * What a tap on the picker row that picks [row] makes of this choice, the prototype's `mRow.pick`:
     *
     * - a persona row: the persona on its model, compare off;
     * - a model row while compare is on: that model becomes the second one, beside [model]; the
     *   prototype then says "comparing sonnet-4.5 ⇄ gpt-5";
     * - a model row otherwise: that model on its own.
     *
     * `null` when nothing changes and the sheet stays open: [model]'s own row while compare is on.
     */
    fun picked(row: ModelChoice): ModelChoice? = when {
        row.persona != null -> ModelChoice(row.persona)
        !compareOn -> ModelChoice(row.model)
        row.model.id == model.id -> null
        else -> ModelChoice(model = model, compareOn = true, compareWith = row.model)
    }
}
