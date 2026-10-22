package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FixedRandom
import br.com.weslleycampos.chat.data.chat.fakes.TestClock
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.toStoredBlock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReplyStreamerTest {
    private val database = FakeChatDatabase(
        conversations = listOf(
            ConversationEntity(id = 1, title = "t", modelId = "sonnet", createdAt = 0, updatedAt = 0),
        ),
        messages = listOf(
            MessageEntity(
                id = REPLY_ID,
                conversationId = 1,
                role = MessageRole.ASSISTANT,
                text = "",
                blocks = emptyList(),
                modelId = "sonnet",
                status = MessageStatus.THINKING,
                latencyMillis = null,
                createdAt = 0,
            ),
        ),
    )

    private fun TestScope.streamer(random: Random = FixedRandom()) =
        ReplyStreamer(messageDao = database.messageDao, clock = TestClock(testScheduler), random = random)

    private val reply get() = database.message(REPLY_ID)

    @Test
    fun aReplyThinksForThreeHundredTwentyMillisecondsAtLeastThenStreamsOneTickEveryTwentyEight() = runTest {
        val streamer = streamer()
        launch { streamer.stream(REPLY_ID, CannedReplies.replies[2]) }

        advanceTimeBy(320 + 28)
        assertEquals(MessageStatus.THINKING, reply.status)
        assertTrue(database.replyUpdates.isEmpty())

        runCurrent()
        assertEquals(MessageStatus.STREAMING, reply.status)
        assertEquals(listOf(StoredBlock.Code(language = "python", text = "from f")), reply.blocks)
        assertEquals(null, reply.latencyMillis)

        advanceTimeBy(28)
        runCurrent()
        assertEquals(listOf(StoredBlock.Code(language = "python", text = "from functoo")), reply.blocks)
        assertEquals(2, database.replyUpdates.size)
    }

    @Test
    fun theThinkingDelayIsAtMostSixHundredNineteenMilliseconds() = runTest {
        val streamer = streamer(random = FixedRandom { until -> until - 1 })
        launch { streamer.stream(REPLY_ID, CannedReplies.replies[2]) }

        advanceTimeBy(619 + 28)
        assertEquals(MessageStatus.THINKING, reply.status)

        runCurrent()
        assertEquals(MessageStatus.STREAMING, reply.status)
    }

    @Test
    fun aFinishedReplyIsDoneWithTheTimeItStreamedForAsItsLatency() = runTest {
        val target = CannedReplies.replies[2]
        val streamer = streamer()
        launch { streamer.stream(REPLY_ID, target) }

        advanceUntilIdle()

        // python code, 132 characters at 6 a tick, then a 72-character paragraph at 2 a tick.
        val ticks = 22 + 36
        assertEquals(MessageStatus.DONE, reply.status)
        assertEquals(target.map(Block::toStoredBlock), reply.blocks)
        assertEquals(ticks * 28L, reply.latencyMillis)
        assertEquals(320 + ticks * 28L, currentTime)
        assertEquals(ticks, database.replyUpdates.size)
        assertEquals(
            List(ticks - 1) { MessageStatus.STREAMING } + MessageStatus.DONE,
            database.replyUpdates.map { it.status },
        )
        assertTrue(database.replyUpdates.dropLast(1).all { it.latencyMillis == null })
    }

    @Test
    fun blocksAreRevealedInOrderAndOnlyEverGrow() = runTest {
        val target = CannedReplies.replies[0].map(Block::toStoredBlock)
        val streamer = streamer(random = Random(seed = 3))
        launch { streamer.stream(REPLY_ID, CannedReplies.replies[0]) }

        advanceUntilIdle()

        val sizes = database.replyUpdates.map { it.blocks.size }
        assertEquals(sizes.sorted(), sizes)
        assertEquals((1..target.size).toList(), sizes.distinct())
        database.replyUpdates.forEach { update ->
            assertEquals(target.take(update.blocks.size - 1), update.blocks.dropLast(1))
        }
        assertEquals(target, database.replyUpdates.last().blocks)
    }

    @Test
    fun aStreamCancelledMidwayLeavesTheReplyInterruptedWithWhatItRevealed() = runTest {
        val streamer = streamer()
        val stream = launch { streamer.stream(REPLY_ID, CannedReplies.replies[1]) }
        advanceTimeBy(320 + 3 * 28)
        runCurrent()

        stream.cancel()
        runCurrent()

        assertEquals(MessageStatus.INTERRUPTED, reply.status)
        assertEquals(listOf(StoredBlock.Paragraph("Shell ")), reply.blocks)
        assertEquals(null, reply.latencyMillis)
    }

    @Test
    fun aStreamCancelledWhileThinkingLeavesTheReplyInterruptedAndEmpty() = runTest {
        val streamer = streamer()
        val stream = launch { streamer.stream(REPLY_ID, CannedReplies.replies[1]) }
        advanceTimeBy(100)

        stream.cancel()
        runCurrent()

        assertEquals(MessageStatus.INTERRUPTED, reply.status)
        assertEquals(emptyList(), reply.blocks)
    }

    @Test
    fun aStreamWhoseWriteFailsLeavesTheReplyInterruptedRatherThanStreaming() = runTest {
        database.beforeReplyUpdate = { if (database.replyUpdates.size == 2) error("disk full") }
        val streamer = streamer()

        val failure = runCatching { streamer.stream(REPLY_ID, CannedReplies.replies[1]) }.exceptionOrNull()

        assertEquals("disk full", failure?.message)
        assertEquals(MessageStatus.INTERRUPTED, reply.status)
        assertEquals(listOf(StoredBlock.Paragraph("Shel")), reply.blocks)
    }

    @Test
    fun aStreamWhoseReplyIsGoneStopsOnTheWriteThatFindsNothingAndEndsQuietly() = runTest {
        val streamer = streamer()
        val stream = launch { streamer.stream(REPLY_ID, CannedReplies.replies[0]) }
        advanceTimeBy(320 + 2 * 28)
        runCurrent()
        assertEquals(2, database.replyUpdates.size)

        database.conversationDao.delete(1)
        advanceUntilIdle()

        // The third tick finds no row to change, and nothing is written or scheduled after it.
        assertEquals(3, database.replyUpdates.size)
        assertEquals(320 + 3 * 28L, currentTime)
        assertTrue(stream.isCompleted && !stream.isCancelled)
        assertTrue(database.messageRows.value.isEmpty())
    }

    private companion object {
        const val REPLY_ID = 2L
    }
}
