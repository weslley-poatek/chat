package br.com.weslleycampos.chat.data.chat.models

/**
 * One model of [ModelCatalogue], with the design prototype's values. There is no provider behind
 * it: every model answers with the same canned replies.
 *
 * @property id Stable key, stored with every conversation and reply: `sonnet`, `gpt5`, …
 * @property name The full name, as the model chip and the empty state show it: "Claude Sonnet 4.5".
 * @property shortName The prototype's `short`, as message meta and the composer placeholder show it:
 *   "sonnet-4.5".
 * @property provider "Anthropic", "OpenAI", …
 * @property contextWindow The prototype's `ctx`, as the empty state's meta line shows it: "200k", "1M".
 * @property cost `$`, `$$` or `$$$`.
 * @property hue The OKLCH hue of the model's accent. `:feature` modules pick the matching `:core:ui`
 *   `ChatPalette` by [id] rather than computing a colour from it.
 */
data class ChatModel(
    val id: String,
    val name: String,
    val shortName: String,
    val provider: String,
    val contextWindow: String,
    val cost: String,
    val hue: Int,
)
