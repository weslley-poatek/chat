package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.core.database.entities.ReplyProgress
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
        ) + listOf(FIRST_SIDE_ID to PAIR_FIRST_SIDE, SECOND_SIDE_ID to PAIR_SECOND_SIDE).map { (id, side) ->
            MessageEntity(
                id = id,
                conversationId = 1,
                role = MessageRole.ASSISTANT,
                text = "",
                blocks = emptyList(),
                modelId = if (side == PAIR_FIRST_SIDE) "sonnet" else "gpt5",
                status = MessageStatus.THINKING,
                latencyMillis = null,
                createdAt = 0,
                pairSide = side,
            )
        },
    )

    private fun TestScope.streamer(random: Random = FixedRandom()) =
        ReplyStreamer(messageDao = database.messageDao, clock = TestClock(testScheduler), random = random)

    private val reply get() = database.message(REPLY_ID)

    /** A compare pair's sides playing the prototype's first reply and its alternative. */
    private val pair = listOf(
        StreamedReply(FIRST_SIDE_ID, CannedReplies.replies[0]),
        StreamedReply(SECOND_SIDE_ID, CannedReplies.alternatives[0]),
    )

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

    @Test
    fun aPairsSidesThinkOnceThenRevealOnTheSameTicksBothInOneWrite() = runTest {
        val streamer = streamer()
        launch { streamer.stream(pair) }

        advanceTimeBy(320 + 28)
        assertEquals(listOf(MessageStatus.THINKING, MessageStatus.THINKING), pairStatuses())
        assertTrue(database.replyUpdates.isEmpty())

        runCurrent()
        assertEquals(listOf(MessageStatus.STREAMING, MessageStatus.STREAMING), pairStatuses())
        assertEquals(listOf(StoredBlock.Paragraph("Tw")), database.message(FIRST_SIDE_ID).blocks)
        assertEquals(listOf(StoredBlock.Paragraph("Mo")), database.message(SECOND_SIDE_ID).blocks)
        assertEquals(listOf(listOf(FIRST_SIDE_ID, SECOND_SIDE_ID)), database.replyBatches.map { it.ids() })

        advanceTimeBy(28)
        runCurrent()
        assertEquals(listOf(StoredBlock.Paragraph("Two ")), database.message(FIRST_SIDE_ID).blocks)
        assertEquals(listOf(StoredBlock.Paragraph("Most")), database.message(SECOND_SIDE_ID).blocks)
        assertEquals(2, database.replyBatches.size)
        assertEquals(2, database.writes)
    }

    @Test
    fun eachSideOfAPairIsDoneOnItsOwnTickWithItsOwnLatencyAndTheOtherPlaysOnAlone() = runTest {
        val streamer = streamer()
        launch { streamer.stream(pair) }

        // REPLY_ALT[0] at the slowest pace: 57 + 22 ticks; REPLIES[0]: 22 + 95 + 6 + 22 + 41.
        advanceTimeBy(320 + 79 * 28)
        runCurrent()
        assertEquals(listOf(MessageStatus.STREAMING, MessageStatus.DONE), pairStatuses())
        assertEquals(CannedReplies.alternatives[0].map(Block::toStoredBlock), database.message(SECOND_SIDE_ID).blocks)
        assertEquals(79 * 28L, database.message(SECOND_SIDE_ID).latencyMillis)
        assertEquals(null, database.message(FIRST_SIDE_ID).latencyMillis)

        advanceUntilIdle()
        assertEquals(listOf(MessageStatus.DONE, MessageStatus.DONE), pairStatuses())
        assertEquals(CannedReplies.replies[0].map(Block::toStoredBlock), database.message(FIRST_SIDE_ID).blocks)
        assertEquals(186 * 28L, database.message(FIRST_SIDE_ID).latencyMillis)
        assertEquals(79 * 28L, database.message(SECOND_SIDE_ID).latencyMillis)
        assertEquals(320 + 186 * 28L, currentTime)
        // One write a tick: both sides together until the second is done, then the first alone.
        assertEquals(186, database.writes)
        assertEquals(79, database.replyBatches.size)
        assertTrue(database.replyBatches.all { it.ids() == listOf(FIRST_SIDE_ID, SECOND_SIDE_ID) })
        assertEquals(79 * 2 + (186 - 79), database.replyUpdates.size)
        assertEquals(1, database.replyUpdates.count { it.id == SECOND_SIDE_ID && it.status == MessageStatus.DONE })
        assertEquals(MessageStatus.DONE, database.replyUpdates.last().status)
    }

    @Test
    fun aPairCancelledMidwayLeavesBothSidesInterruptedWithWhatTheyRevealed() = runTest {
        val streamer = streamer()
        val stream = launch { streamer.stream(pair) }
        advanceTimeBy(320 + 3 * 28)
        runCurrent()

        stream.cancel()
        runCurrent()

        assertEquals(listOf(MessageStatus.INTERRUPTED, MessageStatus.INTERRUPTED), pairStatuses())
        assertEquals(listOf(StoredBlock.Paragraph("Two li")), database.message(FIRST_SIDE_ID).blocks)
        assertEquals(listOf(StoredBlock.Paragraph("Most l")), database.message(SECOND_SIDE_ID).blocks)
        assertTrue(listOf(FIRST_SIDE_ID, SECOND_SIDE_ID).all { database.message(it).latencyMillis == null })
    }

    @Test
    fun aPairCancelledAfterOneSideIsDoneInterruptsOnlyTheOtherSide() = runTest {
        val streamer = streamer()
        val stream = launch { streamer.stream(pair) }
        advanceTimeBy(320 + 100 * 28)
        runCurrent()

        stream.cancel()
        runCurrent()

        assertEquals(listOf(MessageStatus.INTERRUPTED, MessageStatus.DONE), pairStatuses())
        assertEquals(79 * 28L, database.message(SECOND_SIDE_ID).latencyMillis)
    }

    @Test
    fun aPairWhoseRowsAreGoneStopsOnTheWriteThatFindsNothingAndEndsQuietly() = runTest {
        val streamer = streamer()
        val stream = launch { streamer.stream(pair) }
        advanceTimeBy(320 + 2 * 28)
        runCurrent()

        database.conversationDao.delete(1)
        advanceUntilIdle()

        assertEquals(3, database.replyBatches.size)
        assertEquals(320 + 3 * 28L, currentTime)
        assertTrue(stream.isCompleted && !stream.isCancelled)
    }

    private fun pairStatuses(): List<MessageStatus> =
        listOf(FIRST_SIDE_ID, SECOND_SIDE_ID).map { database.message(it).status }

    private fun List<ReplyProgress>.ids(): List<Long> = map { it.id }

    private companion object {
        const val REPLY_ID = 2L
        const val FIRST_SIDE_ID = 3L
        const val SECOND_SIDE_ID = 4L
    }
}
