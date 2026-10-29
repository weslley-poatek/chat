package br.com.weslleycampos.chat.data.fuelix

import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import br.com.weslleycampos.chat.data.fuelix.fakes.FakeFuelix
import br.com.weslleycampos.chat.data.fuelix.fakes.FuelixRequest
import br.com.weslleycampos.chat.data.fuelix.fakes.USAGE_CHUNK
import br.com.weslleycampos.chat.data.fuelix.fakes.chunk
import br.com.weslleycampos.chat.data.fuelix.fakes.events
import br.com.weslleycampos.chat.data.fuelix.fakes.respondEvents
import br.com.weslleycampos.chat.data.fuelix.fakes.respondJson
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every key here is an obvious fake. Fuelix is [FakeFuelix]: Ktor's MockEngine under Koog's client. */
class KoogFuelixClientTest {
    private val fuelixKey = "sk-test-0000000000000000000000000000"
    private val otherFuelixKey = "sk-test-0000000000000000000000001111"

    /** The key Settings has stored, read by every call: [fuelixKey] until a test changes it. */
    private var storedKey: String? = fuelixKey

    private val conversation = listOf(
        FuelixMessage(FuelixRole.SYSTEM, "Answer briefly."),
        FuelixMessage(FuelixRole.USER, "Hi"),
        FuelixMessage(FuelixRole.ASSISTANT, "Hello!"),
        FuelixMessage(FuelixRole.USER, "How are you?"),
    )

    /** `GET /v1/models`, as the OpenAI API lists models. */
    private val models = """{"object":"list","data":[""" +
        """{"id":"gpt-5","object":"model","created":1,"owned_by":"openai"},""" +
        """{"id":"claude-sonnet-4-5","object":"model","created":2,"owned_by":"anthropic"},""" +
        """{"id":"text-embedding-3-large","object":"model","created":3,"owned_by":"openai"}]}"""

    /** An error as the OpenAI API answers one. */
    private val error = """{"error":{"message":"Something went wrong","type":"invalid_request_error"}}"""

    /** What each HTTP error status means. */
    private val httpFailures = listOf(
        401 to "KeyRefused",
        403 to "ModelUnavailable(403)",
        404 to "ModelUnavailable(404)",
        429 to "RateLimited",
        500 to "Failed(500)",
    )

    private fun clientOn(fuelix: FakeFuelix): FuelixClient = KoogFuelixClient(
        keys = FuelixKeySource { storedKey },
        settings = OpenAIClientSettings(baseUrl = FuelixClient.BASE_URL),
        http = fuelix.http,
    )

    @Test
    fun withNoKeyStoredEveryCallThrowsMissingKeyAndSendsNothing() = runTest {
        storedKey = null
        val fuelix = FakeFuelix { respondJson(models) }
        val client = clientOn(fuelix)

        assertFailsWith<FuelixException.MissingKey> { client.models() }
        assertFailsWith<FuelixException.MissingKey> { client.streamChat("gpt-5", conversation).toList() }

        assertEquals(emptyList(), fuelix.requests)
        assertEquals(0, fuelix.clientsMade)
    }

    @Test
    fun aBlankKeyCountsAsNoKey() = runTest {
        val fuelix = FakeFuelix { respondJson(models) }
        val client = clientOn(fuelix)

        for (blank in listOf("", "   \n\t")) {
            storedKey = blank
            assertFailsWith<FuelixException.MissingKey> { client.models() }
            assertFailsWith<FuelixException.MissingKey> { client.streamChat("gpt-5", conversation).toList() }
        }

        assertEquals(emptyList(), fuelix.requests)
        assertEquals(0, fuelix.clientsMade)
    }

    @Test
    fun theModelsAreTheIdsFuelixListsInItsOrderAskedForWithTheKey() = runTest {
        val fuelix = FakeFuelix { respondJson(models) }

        assertEquals(listOf("gpt-5", "claude-sonnet-4-5", "text-embedding-3-large"), clientOn(fuelix).models())

        assertEquals(
            listOf(FuelixRequest("GET", "https://api.fuelix.ai/v1/models", "Bearer $fuelixKey", body = "")),
            fuelix.requests,
        )
    }

    @Test
    fun aReplyIsSentWhenCollectedAsAStreamingChatCompletionOfTheMessagesInOrder() = runTest {
        val fuelix = FakeFuelix { respondEvents(ByteReadChannel(events(chunk("{}", finishReason = "stop"), "[DONE]"))) }
        val reply = clientOn(fuelix).streamChat("gpt-5", conversation)
        assertEquals(emptyList(), fuelix.requests)

        reply.toList()

        val request = fuelix.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://api.fuelix.ai/v1/chat/completions", request.url)
        assertEquals("Bearer $fuelixKey", request.authorization)
        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals("gpt-5", body.getValue("model").jsonPrimitive.content)
        assertTrue(body.getValue("stream").jsonPrimitive.boolean)
        assertEquals(
            listOf("system" to "Answer briefly.", "user" to "Hi", "assistant" to "Hello!", "user" to "How are you?"),
            body.getValue("messages").jsonArray.map { message ->
                val fields = message.jsonObject
                fields.getValue("role").jsonPrimitive.content to fields.getValue("content").jsonPrimitive.content
            },
        )
    }

    @Test
    fun aReplyStreamsItsTextPiecesInOrderAndLeavesOutTheReasoningAndTheWholeTextAgain() = runTest {
        val fuelix = FakeFuelix {
            respondEvents(
                ByteReadChannel(
                    events(
                        chunk("""{"reasoning_content":"The user greets me."}"""),
                        chunk("""{"content":"Hel"}"""),
                        chunk("""{"content":"lo"}"""),
                        chunk("""{"content":", Ana!"}"""),
                        chunk("{}", finishReason = "stop"),
                        USAGE_CHUNK,
                        "[DONE]",
                    ),
                ),
            )
        }

        assertEquals(listOf("Hel", "lo", ", Ana!"), clientOn(fuelix).streamChat("gpt-5", conversation).toList())
    }

    /** OpenAI's first chunk names the role with empty content: no piece, so the reply keeps thinking. */
    @Test
    fun anEmptyPieceIsLeftOut() = runTest {
        val fuelix = FakeFuelix {
            respondEvents(
                ByteReadChannel(
                    events(
                        chunk("""{"role":"assistant","content":""}"""),
                        chunk("""{"content":"Hi"}"""),
                        chunk("{}", finishReason = "stop"),
                        "[DONE]",
                    ),
                ),
            )
        }

        assertEquals(listOf("Hi"), clientOn(fuelix).streamChat("gpt-5", conversation).toList())
    }

    @Test
    fun anHttpErrorForTheModelsThrowsWhatItsStatusMeans() = runTest {
        for ((status, failure) in httpFailures) {
            val fuelix = FakeFuelix { respondJson(error, HttpStatusCode.fromValue(status)) }

            val thrown = assertFailsWith<FuelixException> { clientOn(fuelix).models() }

            assertEquals(failure, thrown.named(), "HTTP $status")
            assertFalse(fuelixKey in thrown.message.orEmpty(), "HTTP $status")
        }
    }

    @Test
    fun anHttpErrorForAReplyThrowsWhatItsStatusMeans() = runTest {
        for ((status, failure) in httpFailures) {
            val fuelix = FakeFuelix { respondJson(error, HttpStatusCode.fromValue(status)) }

            val thrown = assertFailsWith<FuelixException> {
                clientOn(fuelix).streamChat("gpt-5", conversation).toList()
            }

            assertEquals(failure, thrown.named(), "HTTP $status")
            assertFalse(fuelixKey in thrown.message.orEmpty(), "HTTP $status")
        }
    }

    @Test
    fun aConnectionThatFailsThrowsUnreachableForEveryCall() = runTest {
        val fuelix = FakeFuelix { throw IOException("Connection refused") }
        val client = clientOn(fuelix)

        assertEquals("Unreachable", assertFailsWith<FuelixException> { client.models() }.named())
        assertEquals(
            "Unreachable",
            assertFailsWith<FuelixException> { client.streamChat("gpt-5", conversation).toList() }.named(),
        )
    }

    /** Ktor's SSE session reads a broken connection as the end of the stream: no finish reason tells it apart. */
    @Test
    fun aConnectionThatBreaksInTheMiddleOfAReplyThrowsUnreachableAfterThePiecesThatArrived() = runTest {
        val body = ByteChannel()
        body.writeStringUtf8(events(chunk("""{"content":"Hel"}""")))
        body.flush()
        val fuelix = FakeFuelix { respondEvents(body) }
        val pieces = mutableListOf<String>()

        val thrown = assertFailsWith<FuelixException> {
            clientOn(fuelix).streamChat("gpt-5", conversation).collect { piece ->
                pieces += piece
                body.cancel(IOException("Connection reset"))
            }
        }

        assertEquals(listOf("Hel"), pieces)
        assertEquals("Unreachable", thrown.named())
    }

    /** Koog reports the stream's HTTP 200 with the timeout, which is no error's status. */
    @Test
    fun aReplyThatGoesSilentTooLongThrowsUnreachable() = runTest {
        val body = ByteChannel()
        body.writeStringUtf8(events(chunk("""{"content":"Hel"}""")))
        body.flush()
        val fuelix = FakeFuelix { respondEvents(body) }

        val thrown = assertFailsWith<FuelixException> {
            clientOn(fuelix).streamChat("gpt-5", conversation).collect {
                body.cancel(SocketTimeoutException("Socket timeout has expired"))
            }
        }

        assertEquals("Unreachable", thrown.named())
    }

    /** Koog reports the stream's HTTP 200 with the error, which is no error's status. */
    @Test
    fun anErrorSentInTheMiddleOfAReplyThrowsFailedWithNoStatus() = runTest {
        val fuelix = FakeFuelix {
            respondEvents(ByteReadChannel(events(chunk("""{"content":"Hel"}"""), error)))
        }

        val thrown = assertFailsWith<FuelixException> {
            clientOn(fuelix).streamChat("gpt-5", conversation).toList()
        }

        assertEquals("Failed(null)", thrown.named())
    }

    @Test
    fun aChangedKeyIsSentFromTheNextCallOnByANewClientAndTheSameKeyReusesItsClient() = runTest {
        val fuelix = FakeFuelix { respondJson(models) }
        val client = clientOn(fuelix)

        client.models()
        client.models()
        storedKey = otherFuelixKey
        client.models()

        assertEquals(
            listOf("Bearer $fuelixKey", "Bearer $fuelixKey", "Bearer $otherFuelixKey"),
            fuelix.requests.map(FuelixRequest::authorization),
        )
        assertEquals(2, fuelix.clientsMade)
    }

    @Test
    fun aReplyReadsTheKeyWhenItIsCollectedNotWhenItIsAskedFor() = runTest {
        val fuelix = FakeFuelix { respondEvents(ByteReadChannel(events(chunk("{}", finishReason = "stop"), "[DONE]"))) }
        val client = clientOn(fuelix)
        val reply = client.streamChat("gpt-5", conversation)

        storedKey = otherFuelixKey
        reply.toList()
        storedKey = null

        assertEquals(listOf("Bearer $otherFuelixKey"), fuelix.requests.map(FuelixRequest::authorization))
        assertFailsWith<FuelixException.MissingKey> { reply.toList() }
    }
}
