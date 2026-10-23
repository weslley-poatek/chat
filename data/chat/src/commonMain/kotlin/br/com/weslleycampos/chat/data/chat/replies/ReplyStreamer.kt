package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.core.database.daos.MessageDao
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.ReplyProgress
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.toStoredBlock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Plays a canned reply into its message row the way the prototype's `send` and `startStream` do,
 * so the UI only ever reads Room:
 *
 * 1. thinks for [THINKING_MIN] plus up to [THINKING_SPREAD_MILLIS] ms (the prototype's `320 + random * 300` ms),
 *    leaving the row [MessageStatus.THINKING];
 * 2. every [TICK] reveals more of the reply ([BlockReveal]) and writes the visible blocks, as
 *    [MessageStatus.STREAMING];
 * 3. on the tick that completes the last block, writes [MessageStatus.DONE] with the latency: the
 *    time from the end of thinking to that tick, as the prototype's `lat`.
 *
 * A compare pair's two sides play as the prototype's cursors do: they think once, then reveal on
 * the same ticks, each side finishing on its own tick with its own latency. A tick of both sides is
 * one write ([MessageDao.updateReplies]), so observers read them grow together.
 *
 * Every tick is one write, so the UI sees the reply grow at the prototype's pace. A stream that is
 * cancelled or fails leaves its rows [MessageStatus.INTERRUPTED] with the blocks they revealed. One
 * whose rows are gone, deleted with their conversation, stops on the write that finds nothing to
 * change, so it writes nothing more and ends quietly.
 */
internal class ReplyStreamer(
    private val messageDao: MessageDao,
    private val clock: Clock,
    private val random: Random,
) {
    /** Plays [reply] into the row [replyId]. */
    suspend fun stream(replyId: Long, reply: List<Block>) = stream(listOf(StreamedReply(replyId, reply)))

    /** Plays every one of [replies] into its row, thinking once and then ticking them together. */
    suspend fun stream(replies: List<StreamedReply>) {
        try {
            delay(THINKING_MIN + random.nextInt(THINKING_SPREAD_MILLIS).milliseconds)
            val start = clock.now()
            var playing = replies.map { Cursor(it.replyId, BlockReveal(it.blocks)) }
            while (playing.isNotEmpty()) {
                delay(TICK)
                val progress = playing.map { it.advance(start) }
                if (write(progress) == 0) return
                playing = playing.filterNot { it.reveal.isDone }
            }
        } finally {
            // A no-op once a reply is done or gone: it only touches a row still thinking or streaming.
            withContext(NonCancellable) { replies.forEach { messageDao.interruptReply(it.replyId) } }
        }
    }

    /** One tick's writes, in one statement for one reply and in one transaction for a pair's two sides. */
    private suspend fun write(progress: List<ReplyProgress>): Int =
        progress.singleOrNull()?.let { messageDao.updateReply(it) } ?: messageDao.updateReplies(progress)

    /** Where one reply is in its stream. */
    private inner class Cursor(val replyId: Long, val reveal: BlockReveal) {
        /** Reveals one tick more and returns the row's progress after it. */
        fun advance(start: Instant): ReplyProgress {
            val visible = reveal.advance(random)
            val done = reveal.isDone
            return ReplyProgress(
                id = replyId,
                blocks = visible.map(Block::toStoredBlock),
                status = if (done) MessageStatus.DONE else MessageStatus.STREAMING,
                latencyMillis = if (done) (clock.now() - start).inWholeMilliseconds else null,
            )
        }
    }

    companion object {
        val THINKING_MIN = 320.milliseconds
        const val THINKING_SPREAD_MILLIS = 300
        val TICK = 28.milliseconds
    }
}

/** A reply to play: its row, and the canned blocks it reveals there. */
internal class StreamedReply(val replyId: Long, val blocks: List<Block>)
