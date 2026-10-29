package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.ReplyProgress
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.chat.toStoredBlock
import br.com.weslleycampos.chat.data.fuelix.FuelixClient
import br.com.weslleycampos.chat.data.fuelix.FuelixException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Streams a reply from Fuelix into its message row, so the UI only ever reads Room:
 *
 * 1. the row stays [MessageStatus.THINKING] until the first words arrive;
 * 2. as they arrive, the reply so far is written as blocks ([MarkdownBlocks.parse]), as
 *    [MessageStatus.STREAMING], at most once every [WRITE_INTERVAL];
 * 3. once the reply is whole it is written [MessageStatus.DONE], with its latency: the time from its
 *    first words to its last.
 *
 * A compare pair's two sides stream at once, each from its own model, each finishing on its own.
 *
 * A reply that fails, or that Fuelix sends empty, is left [MessageStatus.INTERRUPTED] with what it
 * showed, and the failure is returned for the caller to tell. One that is cancelled is left
 * interrupted too, with no failure. One whose row is gone, deleted with its conversation, stops on
 * the write that finds nothing to change, and ends quietly.
 */
internal class ReplyStreamer(
    private val messageDao: MessageDao,
    private val fuelix: FuelixClient,
    private val clock: Clock,
) {
    /** Streams every one of [requests] at once; returns the first failure among them, or `null`. */
    suspend fun stream(requests: List<ReplyRequest>): ReplyFailure? = coroutineScope {
        requests.map { async { stream(it) } }.awaitAll().firstNotNullOfOrNull { it }
    }

    /** Streams [request] into its row; returns why it failed, or `null` when it finished or its row is gone. */
    suspend fun stream(request: ReplyRequest): ReplyFailure? = try {
        play(request)
    } catch (e: CancellationException) {
        throw e
    } catch (e: FuelixException) {
        e.toReplyFailure()
    } catch (ignored: Exception) {
        ReplyFailure.FAILED
    } finally {
        // A no-op once the reply is done or gone: it only touches a row still thinking or streaming.
        withContext(NonCancellable) { messageDao.interruptReply(request.replyId) }
    }

    private suspend fun play(request: ReplyRequest): ReplyFailure? {
        val text = StringBuilder()
        var first: Instant? = null
        var written: Instant? = null
        try {
            fuelix.streamChat(request.model, request.messages).collect { piece ->
                val now = clock.now()
                if (first == null) first = now
                text.append(piece)
                val last = written
                if (last == null || now - last >= WRITE_INTERVAL) {
                    write(request.replyId, text, MessageStatus.STREAMING, latencyMillis = null)
                    written = now
                }
            }
        } catch (ignored: ReplyGone) {
            return null
        }
        if (text.isBlank()) return ReplyFailure.FAILED
        val latency = clock.now() - (first ?: clock.now())
        return try {
            write(request.replyId, text, MessageStatus.DONE, latencyMillis = latency.inWholeMilliseconds)
            null
        } catch (ignored: ReplyGone) {
            null
        }
    }

    /** Writes the reply so far; throws [ReplyGone] when its row is not there to write. */
    private suspend fun write(replyId: Long, text: CharSequence, status: MessageStatus, latencyMillis: Long?) {
        val progress = ReplyProgress(
            id = replyId,
            blocks = MarkdownBlocks.parse(text.toString()).map(Block::toStoredBlock),
            status = status,
            latencyMillis = latencyMillis,
        )
        if (messageDao.updateReply(progress) == 0) throw ReplyGone()
    }

    /** The reply's row was deleted with its conversation: the stream stops. */
    private class ReplyGone : Exception()

    companion object {
        /** How often a streaming reply is written at most: about the pace the design reveals at. */
        val WRITE_INTERVAL = 40.milliseconds
    }
}

/** The [ReplyFailure] a Fuelix failure means. */
internal fun FuelixException.toReplyFailure(): ReplyFailure = when (this) {
    is FuelixException.MissingKey -> ReplyFailure.NO_KEY
    is FuelixException.KeyRefused -> ReplyFailure.KEY_REFUSED
    is FuelixException.ModelUnavailable -> ReplyFailure.MODEL_UNAVAILABLE
    is FuelixException.RateLimited -> ReplyFailure.RATE_LIMITED
    is FuelixException.Unreachable -> ReplyFailure.UNREACHABLE
    is FuelixException.Failed -> ReplyFailure.FAILED
}
