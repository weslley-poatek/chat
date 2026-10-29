package br.com.weslleycampos.chat.data.settings.models

/**
 * The model providers Settings keeps an API key for: the "api keys" group's rows, in [entries]
 * order. There is one, Fuelix, the gateway at `https://api.fuelix.ai` that serves every model of
 * the chat's model catalogue, so its one key covers them all. Every reply is asked of it, so its
 * key is sent to it, and only to it, with each request.
 *
 * @property id The stable name its key is stored under. Never shown; never rename it, or the key
 *   stored under the old name reads as not set.
 * @property displayName The row's name.
 * @property placeholder The edit row's input placeholder.
 */
enum class ModelProvider(val id: String, val displayName: String, val placeholder: String) {
    FUELIX(id = "fuelix", displayName = "Fuelix", placeholder = "paste key"),
}
