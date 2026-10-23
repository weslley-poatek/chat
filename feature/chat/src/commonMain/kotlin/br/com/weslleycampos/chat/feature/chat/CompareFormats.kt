package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelChoice

// Compare mode's texts, as the prototype writes them: pure, so the tests pin each one.

/** Between the two models of a compare, the prototype's `" ⇄ "`. */
internal const val CompareArrow = " ⇄ "

/**
 * Two compared models by their short names, the prototype's `` `${m.short} ⇄ ${cmp.short}` ``:
 * "sonnet-4.5 ⇄ gpt-5", the chip and the compare hint.
 */
fun compareLabel(model: ChatModel, comparing: ChatModel): String =
    "${model.shortName}$CompareArrow${comparing.shortName}"

/**
 * The composer's placeholder, the prototype's `placeholder` (line 897): "ask sonnet-4.5 and gpt-5…"
 * while two models are compared, else "message sonnet-4.5…".
 */
fun composerPlaceholder(choice: ModelChoice): String {
    val comparing = choice.comparing ?: return "message ${choice.model.shortName}…"
    return "ask ${choice.model.shortName} and ${comparing.shortName}…"
}

/**
 * A compare pair's meta row, the prototype's pair `meta` (line 813): its two models' short names and
 * the time, without latency, "sonnet-4.5 ⇄ gpt-5 · 14:05".
 */
fun compareMeta(pair: MessageUi.ComparePair): String =
    pair.sides.joinToString(CompareArrow) { it.modelShortName } + MetaSeparator + pair.time

/**
 * A compare card's header: the model's full name, as the prototype's `p.label`, then its latency once
 * it is done ("Claude Sonnet 4.5 · 1.9s"), or [interruptedLabel] for a side that stopped on the way.
 */
fun compareSideLabel(side: CompareSideUi, interruptedLabel: String): String =
    listOfNotNull(side.modelName, side.latency, interruptedLabel.takeIf { side.interrupted })
        .joinToString(MetaSeparator)
