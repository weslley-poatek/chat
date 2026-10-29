package br.com.weslleycampos.chat.data.chat.models

/**
 * One model Fuelix serves, as [ModelCatalogue] reads it from its id. Replies come from Fuelix, on
 * [id].
 *
 * @property id Fuelix's model id, sent with every request and stored with every conversation and
 *   reply: `claude-sonnet-4-5-20250929`, `gpt-5`, … Ids stored before Fuelix (`sonnet`, `gpt5`, …)
 *   read as the model they became ([ModelCatalogue.resolve]).
 * @property name The full name, as the model chip and the empty state show it: "Claude Sonnet 4.5".
 * @property shortName As message meta and the composer placeholder show it: "sonnet-4.5".
 * @property vendor Who makes it: the picker's group, and the accent every one of its models wears.
 * @property contextWindow The empty state's and picker's "200k" or "1M", or `null` when unknown.
 * @property cost `$`, `$$` or `$$$`, or `null` when unknown.
 */
data class ChatModel(
    val id: String,
    val name: String,
    val shortName: String,
    val vendor: ModelVendor,
    val contextWindow: String? = null,
    val cost: String? = null,
) {
    /** "Anthropic", "OpenAI", …: [vendor]'s name. */
    val provider: String get() = vendor.displayName

    /** The OKLCH hue of [vendor]'s accent. */
    val hue: Int get() = vendor.hue
}

/**
 * Who makes a model, in the picker's order, read from the start of its id ([of]). Each vendor keeps
 * the design's accent for its models: `:feature` modules pick the matching `:core:ui` `ChatPalette`
 * by vendor rather than computing a colour from [hue].
 *
 * @property displayName The picker's group header and a model's provider: "Anthropic".
 * @property hue The OKLCH hue of its accent.
 * @property prefixes How its models' ids start, lowercase.
 */
enum class ModelVendor(val displayName: String, val hue: Int, private val prefixes: List<String>) {
    ANTHROPIC(displayName = "Anthropic", hue = 35, prefixes = listOf("claude")),
    OPENAI(
        displayName = "OpenAI",
        hue = 150,
        prefixes = listOf("gpt", "chatgpt", "o1", "o3", "o4", "codex", "text-embedding", "whisper", "tts", "dall-e"),
    ),
    GOOGLE(displayName = "Google", hue = 255, prefixes = listOf("gemini", "gemma", "imagen")),
    META(displayName = "Meta", hue = 290, prefixes = listOf("llama")),
    DEEPSEEK(displayName = "DeepSeek", hue = 220, prefixes = listOf("deepseek")),
    MISTRAL(
        displayName = "Mistral",
        hue = 80,
        prefixes = listOf("mistral", "magistral", "ministral", "codestral", "devstral", "pixtral", "voxtral"),
    ),

    /** Every model whose id names none of the above, such as TELUS's own and Qwen; Anthropic's accent. */
    OTHER(displayName = "Other", hue = 35, prefixes = emptyList()),
    ;

    companion object {
        /** The vendor whose prefix [id] starts with, ignoring case, or [OTHER]. */
        fun of(id: String): ModelVendor {
            val lower = id.lowercase()
            return entries.firstOrNull { vendor -> vendor.prefixes.any(lower::startsWith) } ?: OTHER
        }
    }
}
