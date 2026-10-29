package br.com.weslleycampos.chat.data.fuelix

import kotlinx.coroutines.flow.Flow

/**
 * Fuelix, the gateway at [FuelixClient.BASE_URL] that serves every model the app talks to, on its
 * OpenAI-compatible API, authenticated with the key Settings stores ([FuelixKeySource]). Koog's
 * OpenAI client makes the calls.
 *
 * Every call reads the key when it starts, so a key saved or removed in Settings counts from the
 * next call on. A call that cannot be answered throws a [FuelixException] that says why; the key
 * is never part of it, nor of anything logged.
 */
interface FuelixClient {
    /**
     * The ids of every model the key may use, `GET /v1/models`, in Fuelix's order: chat models and
     * any others (embeddings, images, audio) alike.
     */
    suspend fun models(): List<String>

    /**
     * A chat reply from [model] to [messages], `POST /v1/chat/completions` with `stream: true`: the
     * text as it arrives, a piece at a time, completing when the reply is whole. The request is sent
     * when the flow is collected, and cancelling the collection cancels it.
     */
    fun streamChat(model: String, messages: List<FuelixMessage>): Flow<String>

    companion object {
        const val BASE_URL = "https://api.fuelix.ai"
    }
}

/** One message of a chat request: who said it and what. */
data class FuelixMessage(val role: FuelixRole, val content: String)

/** A chat message's `role`. */
enum class FuelixRole(val value: String) {
    /** Instructions for the model, such as a persona's prompt. */
    SYSTEM("system"),

    /** What the user said. */
    USER("user"),

    /** What a model said back. */
    ASSISTANT("assistant"),
}

/**
 * The Fuelix key, whole, for a request's `Authorization` header. `:data:settings` keeps it and
 * provides this; nothing else reads a whole key.
 */
fun interface FuelixKeySource {
    /** The stored key, or `null` while none is. */
    suspend fun key(): String?
}
