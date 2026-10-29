package br.com.weslleycampos.chat.data.chat.fakes

import br.com.weslleycampos.chat.data.fuelix.FuelixClient
import br.com.weslleycampos.chat.data.fuelix.FuelixException
import br.com.weslleycampos.chat.data.fuelix.FuelixMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fuelix as a script, on the test's virtual time. A stream sends its pieces one every [pieceDelay],
 * then ends, or throws the failure scripted for it:
 *
 * - [reply] queues what a model's next stream sends; a model with nothing queued answers
 *   "Answer from <model>." in [DEFAULT_PIECES] pieces ([answer]);
 * - [hold] keeps streams waiting once they have sent some pieces, a model's or every one, until
 *   [release]: a reply held at `0` stays thinking, one held later stays streaming;
 * - [calls] records every stream, its model, what it was sent and how many pieces it sent.
 *
 * [models] is what `models()` lists, or [modelsFailure] what it throws.
 */
internal class FakeFuelixClient(var pieceDelay: Duration = PIECE_DELAY) : FuelixClient {
    /** One stream: the request it was sent and how far it got. */
    class Call(val model: String, val messages: List<FuelixMessage>) {
        /** How many pieces it sent, the one its collector stopped on included. */
        var sent = 0
            internal set
    }

    private class Script(val pieces: List<String>, val failure: FuelixException?)

    private val queued = mutableMapOf<String, ArrayDeque<Script>>()

    /** How many pieces streams wait after, by model; the `null` key holds every model's. */
    private val holds = MutableStateFlow<Map<String?, Int>>(emptyMap())

    private val recorded = mutableListOf<Call>()

    /** Every stream collected so far, in the order they started. */
    val calls: List<Call> get() = recorded

    /** What `models()` returns. */
    var models: List<String> = emptyList()

    /** What `models()` throws instead, when set. */
    var modelsFailure: FuelixException? = null

    /** How many times `models()` was called. */
    var modelsCalls = 0
        private set

    /** Queues [model]'s next stream: [pieces] in order, then [failure] when there is one. */
    fun reply(model: String, vararg pieces: String, failure: FuelixException? = null) {
        queued.getOrPut(model) { ArrayDeque() }.addLast(Script(pieces.toList(), failure))
    }

    /** Holds [model]'s streams, or every model's for `null`, once they have sent [afterPieces] pieces. */
    fun hold(afterPieces: Int = 0, model: String? = null) {
        holds.update { it + (model to afterPieces) }
    }

    /** Lets every held stream go on. */
    fun release() {
        holds.value = emptyMap()
    }

    override suspend fun models(): List<String> {
        modelsCalls++
        modelsFailure?.let { throw it }
        return models
    }

    override fun streamChat(model: String, messages: List<FuelixMessage>): Flow<String> = flow {
        val call = Call(model, messages)
        recorded += call
        val script = queued[model]?.removeFirstOrNull() ?: Script(answerPieces(model), failure = null)
        script.pieces.forEachIndexed { index, piece ->
            waitWhileHeld(model, sent = index)
            delay(pieceDelay)
            call.sent++
            emit(piece)
        }
        waitWhileHeld(model, sent = script.pieces.size)
        script.failure?.let { throw it }
    }

    private suspend fun waitWhileHeld(model: String, sent: Int) {
        holds.first { held -> held[model] != sent && held[null] != sent }
    }

    companion object {
        /** How long each piece takes by default: every piece of a reply is written as it arrives. */
        val PIECE_DELAY = 50.milliseconds

        /** How many pieces a model's default answer comes in. */
        const val DEFAULT_PIECES = 4

        /** What a model with nothing queued answers. */
        fun answer(model: String): String = answerPieces(model).joinToString("")

        private fun answerPieces(model: String): List<String> = listOf("Answer ", "from ", model, ".")
    }
}
