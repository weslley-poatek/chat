package br.com.weslleycampos.chat.data.settings.models

/** The prototype's placeholder for every provider without a key prefix of its own. */
private const val PASTE_KEY = "paste key"

/**
 * The model providers Settings keeps an API key for: the "api keys" group's rows, in [entries]
 * order, the prototype's Anthropic, OpenAI, Google, Meta, DeepSeek, Mistral. They are the providers
 * of the chat's model catalogue. Replies stay canned, so a stored key is never sent to one.
 *
 * @property id The stable name its key is stored under. Never shown; never rename it, or the key
 *   stored under the old name reads as not set.
 * @property displayName The row's name, as the prototype and the model catalogue write it.
 * @property placeholder The edit row's input placeholder: the key's usual prefix and an ellipsis
 *   where the provider has one, "paste key" otherwise.
 */
enum class ModelProvider(val id: String, val displayName: String, val placeholder: String) {
    ANTHROPIC(id = "anthropic", displayName = "Anthropic", placeholder = "sk-ant-…"),
    OPENAI(id = "openai", displayName = "OpenAI", placeholder = "sk-…"),
    GOOGLE(id = "google", displayName = "Google", placeholder = PASTE_KEY),
    META(id = "meta", displayName = "Meta", placeholder = PASTE_KEY),
    DEEPSEEK(id = "deepseek", displayName = "DeepSeek", placeholder = PASTE_KEY),
    MISTRAL(id = "mistral", displayName = "Mistral", placeholder = PASTE_KEY),
}
