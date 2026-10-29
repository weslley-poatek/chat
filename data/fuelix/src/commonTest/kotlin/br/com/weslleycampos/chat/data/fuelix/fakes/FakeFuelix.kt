package br.com.weslleycampos.chat.data.fuelix.fakes

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.sse.SSECapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.ResponseAdapterAttributeKey
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.serialization.json.Json

/** A request Fuelix got: its method, whole URL, `Authorization` header and body. */
internal data class FuelixRequest(val method: String, val url: String, val authorization: String?, val body: String)

/**
 * Fuelix's API played by Ktor's [MockEngine], under Koog's own Ktor client ([http]), as the app's
 * client is built on. [reply] answers every request, and [requests] records them in order;
 * [clientsMade] counts the Koog clients [http] made, one per key.
 */
internal class FakeFuelix(private val reply: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val requests = mutableListOf<FuelixRequest>()

    var clientsMade = 0
        private set

    private val engine = MockEngine { request ->
        requests += FuelixRequest(
            method = request.method.value,
            url = request.url.toString(),
            authorization = request.headers[HttpHeaders.Authorization],
            body = request.body.toByteArray().decodeToString(),
        )
        reply(request)
    }

    private val ktor = KtorKoogHttpClient.Factory(baseClient = HttpClient(SseMockEngine(engine)))

    val http = object : KoogHttpClient.Factory {
        override fun create(
            clientName: String,
            baseUrl: String,
            headers: Map<String, String>,
            queryParameters: Map<String, String>,
            requestTimeoutMillis: Long,
            connectTimeoutMillis: Long,
            socketTimeoutMillis: Long,
            json: Json,
        ): KoogHttpClient {
            clientsMade++
            return ktor.create(
                clientName = clientName,
                baseUrl = baseUrl,
                headers = headers,
                queryParameters = queryParameters,
                requestTimeoutMillis = requestTimeoutMillis,
                connectTimeoutMillis = connectTimeoutMillis,
                socketTimeoutMillis = socketTimeoutMillis,
                json = json,
            )
        }
    }
}

/**
 * [mock] made able to serve Ktor's SSE plugin, which [MockEngine] 3.6.0 cannot as it is: the plugin
 * asks the engine for [SSECapability], which [MockEngine] does not list, so the request fails before
 * it is made; and it expects the response body as the SSE session that the request's
 * [ResponseAdapterAttributeKey] adapter makes, which real engines call and [MockEngine] does not.
 * This lists the capability and calls the adapter on [mock]'s answer, as OkHttp and Darwin do.
 */
@OptIn(InternalAPI::class)
private class SseMockEngine(private val mock: MockEngine) : HttpClientEngine by mock {
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> =
        mock.supportedCapabilities + SSECapability

    /** The default install, so its requests reach this engine's [execute], not [mock]'s. */
    override fun install(client: HttpClient) = super<HttpClientEngine>.install(client)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val response = mock.execute(data)
        val body = response.body as? ByteReadChannel ?: return response
        val session = data.attributes.getOrNull(ResponseAdapterAttributeKey)
            ?.adapt(data, response.statusCode, response.headers, body, data.body, response.callContext)
            ?: return response
        return HttpResponseData(
            statusCode = response.statusCode,
            requestTime = response.requestTime,
            headers = response.headers,
            version = response.version,
            body = session,
            callContext = response.callContext,
        )
    }
}

/** A JSON answer with [status]. */
internal fun MockRequestHandleScope.respondJson(json: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = json, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

/** An SSE answer whose body is [body], as Chat Completions streams one. */
internal fun MockRequestHandleScope.respondEvents(body: ByteReadChannel) = respond(
    content = body,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
)

/** [data] as SSE `data:` events, one per item, in order. */
internal fun events(vararg data: String): String = data.joinToString("") { "data: $it\n\n" }

/**
 * A Chat Completions stream chunk for `gpt-5` whose first choice's delta is [delta], a JSON object,
 * ending the reply with [finishReason] when one is given.
 */
internal fun chunk(delta: String, finishReason: String? = null): String {
    val finish = finishReason?.let { ""","finish_reason":"$it"""" }.orEmpty()
    return """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"gpt-5",""" +
        """"choices":[{"index":0,"delta":$delta$finish}]}"""
}

/** The last chunk of a stream asked with `include_usage`: no choices, only the tokens used. */
internal const val USAGE_CHUNK = """{"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,""" +
    """"model":"gpt-5","choices":[],"usage":{"prompt_tokens":9,"completion_tokens":3,"total_tokens":12}}"""
