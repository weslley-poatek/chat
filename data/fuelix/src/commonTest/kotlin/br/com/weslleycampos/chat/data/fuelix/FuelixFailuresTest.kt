package br.com.weslleycampos.chat.data.fuelix

import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.executor.clients.LLMClientException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/** [failureFor] and [toFuelixException]: what a failure of Koog's client means to the app. */
class FuelixFailuresTest {
    private fun httpError(status: Int?, cause: Throwable? = null) =
        KoogHttpClientException(clientName = "OpenAILLMClient", statusCode = status, errorBody = "{}", cause = cause)

    @Test
    fun anHttpStatusMeansTheKeyTheModelTheRateOrAnyOtherFailureWithItsStatus() {
        assertEquals("KeyRefused", failureFor(401).named())
        assertEquals("ModelUnavailable(403)", failureFor(403).named())
        assertEquals("ModelUnavailable(404)", failureFor(404).named())
        assertEquals("RateLimited", failureFor(429).named())
        for (status in listOf(400, 402, 408, 409, 422, 500, 502, 503, 504)) {
            assertEquals("Failed($status)", failureFor(status).named())
        }
    }

    @Test
    fun aKoogFailureMeansTheFirstHttpStatusAmongItsCauses() {
        assertEquals("KeyRefused", httpError(401).toFuelixException().named())
        assertEquals(
            "RateLimited",
            LLMClientException("OpenAILLMClient", "Streaming failed", httpError(429)).toFuelixException().named(),
        )
        assertEquals(
            "ModelUnavailable(404)",
            httpError(status = null, cause = httpError(404)).toFuelixException().named(),
        )
    }

    @Test
    fun aConnectionFailureAmongTheCausesWithoutAStatusIsUnreachableAndKeepsTheFailure() {
        val failure = LLMClientException("OpenAILLMClient", "Streaming failed", httpError(null, IOException("Reset")))

        val unreachable = failure.toFuelixException()

        assertIs<FuelixException.Unreachable>(unreachable)
        assertSame(failure, unreachable.cause)
        assertEquals("Unreachable", IOException("Connection refused").toFuelixException().named())
    }

    @Test
    fun anyOtherFailureIsFailedWithNoStatus() {
        assertEquals("Failed(null)", SerializationException("Unexpected JSON token").toFuelixException().named())
        assertEquals("Failed(null)", httpError(status = null).toFuelixException().named())
        assertEquals("Failed(null)", IllegalStateException("Empty choices").toFuelixException().named())
    }

    @Test
    fun aFuelixExceptionIsThrownAsItIs() {
        val missing = FuelixException.MissingKey()
        val refused = FuelixException.KeyRefused()

        assertSame(missing, missing.toFuelixException())
        assertSame(refused, refused.toFuelixException())
    }
}
