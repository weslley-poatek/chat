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
 * Every tick is one write, so the UI sees the reply grow at the prototype's pace. A stream that is
 * cancelled or fails leaves the row [MessageStatus.INTERRUPTED] with the blocks it revealed. One
 * whose row is gone, deleted with its conversation, stops on the write that finds nothing to
 * change, so it writes nothing more and ends quietly.
 */
internal class ReplyStreamer(
    private val messageDao: MessageDao,
    private val clock: Clock,
    private val random: Random,
) {
    suspend fun stream(replyId: Long, reply: List<Block>) {
        try {
            delay(THINKING_MIN + random.nextInt(THINKING_SPREAD_MILLIS).milliseconds)
            val start = clock.now()
            val reveal = BlockReveal(reply)
            while (!reveal.isDone) {
                delay(TICK)
                val visible = reveal.advance(random)
                val done = reveal.isDone
                val written = messageDao.updateReply(
                    ReplyProgress(
                        id = replyId,
                        blocks = visible.map(Block::toStoredBlock),
                        status = if (done) MessageStatus.DONE else MessageStatus.STREAMING,
                        latencyMillis = if (done) (clock.now() - start).inWholeMilliseconds else null,
                    ),
                )
                if (written == 0) return
            }
        } finally {
            // A no-op once the reply is done or gone: it only touches a row still thinking or streaming.
            withContext(NonCancellable) { messageDao.interruptReply(replyId) }
        }
    }

    companion object {
        val THINKING_MIN = 320.milliseconds
        const val THINKING_SPREAD_MILLIS = 300
        val TICK = 28.milliseconds
    }
}
