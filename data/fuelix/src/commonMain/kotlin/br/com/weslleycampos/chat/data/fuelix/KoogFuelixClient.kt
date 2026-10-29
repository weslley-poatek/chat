package br.com.weslleycampos.chat.data.fuelix

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException

/**
 * [FuelixClient] on Koog's [OpenAILLMClient], pointed at Fuelix with [settings] and the key from
 * [keys], its HTTP calls made by [http]. A reply streams from Chat Completions; Koog's non-empty
 * text deltas are its pieces, and the rest of what it streams (reasoning, the whole text again) is
 * left out. A reply whose stream ends with no finish reason was cut off, since Fuelix's
 * OpenAI-compatible stream always ends with one: Ktor reads a connection that breaks mid-reply as
 * the end of the stream, so it throws [FuelixException.Unreachable] after the pieces that arrived.
 *
 * Koog's client holds the key it was made with, so one is made per key and kept until the key
 * changes. A failure is thrown as the [FuelixException] it means ([toFuelixException]).
 */
internal class KoogFuelixClient(
    private val keys: FuelixKeySource,
    private val settings: OpenAIClientSettings,
    private val http: KoogHttpClient.Factory,
) : FuelixClient {
    private val lock = Mutex()
    private var current: KeyedClient? = null

    override suspend fun models(): List<String> = try {
        client().models().map(LLModel::id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toFuelixException()
    }

    override fun streamChat(model: String, messages: List<FuelixMessage>): Flow<String> = flow<String> {
        val frames: Flow<StreamFrame> = client().executeStreaming(promptOf(messages), chatModel(model))
        frames.collect { frame ->
            when (frame) {
                is StreamFrame.TextDelta -> if (frame.text.isNotEmpty()) emit(frame.text)
                is StreamFrame.End -> if (frame.finishReason == null) throw FuelixException.Unreachable(CutOff())
                else -> Unit
            }
        }
    }.catch { e -> throw if (e is CancellationException) e else e.toFuelixException() }

    /** Koog's client for the stored key, made when the key is first seen. */
    private suspend fun client(): OpenAILLMClient {
        val key = keys.key()?.takeIf(String::isNotBlank) ?: throw FuelixException.MissingKey()
        return lock.withLock {
            current?.takeIf { it.key == key }?.client
                ?: OpenAILLMClient(apiKey = key, settings = settings, httpClientFactory = http)
                    .also { current = KeyedClient(key, it) }
        }
    }

    /** A reply's stream that ended before Fuelix finished the reply. */
    private class CutOff : IOException("The reply's stream ended before Fuelix finished it")

    /** A Koog client and the key it sends. */
    private class KeyedClient(val key: String, val client: OpenAILLMClient)

    private companion object {
        const val PROMPT_ID = "fuelix-chat"

        /** [messages] as Koog's prompt, in their order. */
        fun promptOf(messages: List<FuelixMessage>): Prompt = prompt(PROMPT_ID) {
            for (message in messages) {
                when (message.role) {
                    FuelixRole.SYSTEM -> system(message.content)
                    FuelixRole.USER -> user(message.content)
                    FuelixRole.ASSISTANT -> assistant(message.content)
                }
            }
        }

        /**
         * Fuelix model [id] as a model Koog sends to Chat Completions, whatever its vendor: Koog asks
         * a model for the capabilities a request needs, and Fuelix serves every model there.
         */
        fun chatModel(id: String): LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = id,
            capabilities = listOf(LLMCapability.Completion, LLMCapability.OpenAIEndpoint.Completions),
        )
    }
}

/**
 * The [FuelixException] a Koog failure means: the HTTP error status it carries ([failureFor]); else
 * [FuelixException.Unreachable] when a connection failed, broke or timed out on the way; else
 * [FuelixException.Failed], such as for an error sent in the middle of a reply or an answer that is
 * not what the API describes. A success status is not an error's: a reply that fails once it has
 * started carries its stream's HTTP 200.
 */
internal fun Throwable.toFuelixException(): FuelixException {
    if (this is FuelixException) return this
    val causes = generateSequence(this, Throwable::cause)
    val status = causes.filterIsInstance<KoogHttpClientException>()
        .firstNotNullOfOrNull { it.statusCode?.takeUnless { code -> code in HTTP_SUCCESS } }
    return when {
        status != null -> failureFor(status)
        causes.any { it is IOException } -> FuelixException.Unreachable(this)
        else -> FuelixException.Failed(status = null)
    }
}

/** The [FuelixException] an HTTP error [status] means. */
internal fun failureFor(status: Int): FuelixException = when (status) {
    HTTP_UNAUTHORIZED -> FuelixException.KeyRefused()
    HTTP_FORBIDDEN, HTTP_NOT_FOUND -> FuelixException.ModelUnavailable(status)
    HTTP_TOO_MANY_REQUESTS -> FuelixException.RateLimited()
    else -> FuelixException.Failed(status)
}

private val HTTP_SUCCESS = 200..299
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
