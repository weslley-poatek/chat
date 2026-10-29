package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakeFuelixClient
import br.com.weslleycampos.chat.data.chat.fakes.FakeFuelixClient.Companion.PIECE_DELAY
import br.com.weslleycampos.chat.data.chat.fakes.TestClock
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.fuelix.FuelixException
import br.com.weslleycampos.chat.data.fuelix.FuelixMessage
import br.com.weslleycampos.chat.data.fuelix.FuelixRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [ReplyStreamer] on a fake Fuelix whose pieces arrive one every [PIECE_DELAY] of virtual time:
 * a default answer, "Answer from <model>.", comes in four pieces, its last 200 ms after the request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplyStreamerTest {
    private val fuelix = FakeFuelixClient()

    private val database = FakeChatDatabase(
        conversations = listOf(
            ConversationEntity(id = 1, title = "t", modelId = GPT, createdAt = 0, updatedAt = 0),
        ),
        messages = listOf(
            thinking(id = REPLY_ID, modelId = GPT),
            thinking(id = FIRST_SIDE_ID, modelId = SONNET, pairSide = PAIR_FIRST_SIDE),
            thinking(id = SECOND_SIDE_ID, modelId = GPT, pairSide = PAIR_SECOND_SIDE),
        ),
    )

    private val question = listOf(FuelixMessage(FuelixRole.USER, "SSE or WebSocket?"))

    private fun TestScope.streamer() =
        ReplyStreamer(messageDao = database.messageDao, fuelix = fuelix, clock = TestClock(testScheduler))

    private fun request(replyId: Long = REPLY_ID, model: String = GPT) =
        ReplyRequest(replyId = replyId, model = model, messages = question)

    /** A compare pair's sides: Sonnet first, GPT-5 second. */
    private val pair = listOf(request(FIRST_SIDE_ID, SONNET), request(SECOND_SIDE_ID, GPT))

    private val reply get() = database.message(REPLY_ID)

    private fun paragraph(text: String) = listOf(StoredBlock.Paragraph(text))

    @Test
    fun aReplyThinksUntilItsFirstWordsArriveThenStreamsTheReplySoFar() = runTest {
        fuelix.reply(GPT, "Use ", "SSE.")
        val streamer = streamer()
        launch { streamer.stream(request()) }

        advanceTimeBy(PIECE_DELAY)
        assertEquals(MessageStatus.THINKING, reply.status)
        assertTrue(database.replyUpdates.isEmpty())

        runCurrent()
        assertEquals(MessageStatus.STREAMING, reply.status)
        assertEquals(paragraph("Use"), reply.blocks)
        assertNull(reply.latencyMillis)
    }

    @Test
    fun aFinishedReplyIsDoneWithItsWholeTextAndTheTimeFromItsFirstWordsToItsLastAsItsLatency() = runTest {
        fuelix.hold(afterPieces = 0)
        val streamer = streamer()
        val result = async { streamer.stream(request()) }
        advanceTimeBy(10.seconds)
        assertEquals(MessageStatus.THINKING, reply.status)

        fuelix.release()
        advanceUntilIdle()

        assertNull(result.await())
        assertEquals(MessageStatus.DONE, reply.status)
        assertEquals(paragraph("Answer from gpt-5."), reply.blocks)
        // Four pieces 50 ms apart: thinking is not part of the latency.
        assertEquals(150L, reply.latencyMillis)
        assertEquals(
            List(FakeFuelixClient.DEFAULT_PIECES) { MessageStatus.STREAMING } + MessageStatus.DONE,
            database.replyUpdates.map { it.status },
        )
        val call = fuelix.calls.single()
        assertEquals(GPT to question, call.model to call.messages)
    }

    @Test
    fun aStreamingReplyIsWrittenAtMostOnceEveryFortyMilliseconds() = runTest {
        fuelix.pieceDelay = 10.milliseconds
        fuelix.reply(GPT, *Array(10) { "w$it " })
        val streamer = streamer()

        streamer.stream(request())

        // Pieces at 10, 20, … 100 ms: written at 10, 50 and 90, then whole and done at 100.
        fun words(count: Int) = (0 until count).joinToString(" ") { "w$it" }
        assertEquals(
            listOf(words(1), words(5), words(9), words(10)),
            database.replyUpdates.map { (it.blocks.single() as StoredBlock.Paragraph).text },
        )
        assertEquals(
            listOf(MessageStatus.STREAMING, MessageStatus.STREAMING, MessageStatus.STREAMING, MessageStatus.DONE),
            database.replyUpdates.map { it.status },
        )
        assertEquals(90L, reply.latencyMillis)
    }

    @Test
    fun theReplySoFarIsReadAsMarkdownAnOpenCodeBlockIncluded() = runTest {
        fuelix.reply(GPT, "Try this:\n", "```kotlin\nval x", " = 1\n```\n", "Done.")
        fuelix.hold(afterPieces = 2)
        val streamer = streamer()
        launch { streamer.stream(request()) }

        advanceUntilIdle()
        assertEquals(MessageStatus.STREAMING, reply.status)
        assertEquals(
            listOf(StoredBlock.Paragraph("Try this:"), StoredBlock.Code(language = "kotlin", text = "val x")),
            reply.blocks,
        )

        fuelix.release()
        advanceUntilIdle()
        assertEquals(MessageStatus.DONE, reply.status)
        assertEquals(
            listOf(
                StoredBlock.Paragraph("Try this:"),
                StoredBlock.Code(language = "kotlin", text = "val x = 1"),
                StoredBlock.Paragraph("Done."),
            ),
            reply.blocks,
        )
    }

    @Test
    fun anEmptyOrBlankReplyFailsAndIsLeftInterruptedShowingNothing() = runTest {
        fuelix.reply(GPT)
        fuelix.reply(SONNET, " ", "\n\n")
        val streamer = streamer()

        assertEquals(ReplyFailure.FAILED, streamer.stream(request()))
        assertEquals(ReplyFailure.FAILED, streamer.stream(request(FIRST_SIDE_ID, SONNET)))

        assertEquals(MessageStatus.INTERRUPTED, reply.status)
        assertEquals(emptyList(), reply.blocks)
        assertEquals(MessageStatus.INTERRUPTED, database.message(FIRST_SIDE_ID).status)
        assertEquals(emptyList(), database.message(FIRST_SIDE_ID).blocks)
        assertTrue(database.replyUpdates.none { it.status == MessageStatus.DONE })
    }

    @Test
    fun aFuelixFailureIsReturnedAndTheReplyIsLeftInterruptedWithWhatItShowed() = runTest {
        fuelix.reply(GPT, "Two ", "lines", failure = FuelixException.RateLimited())
        val streamer = streamer()

        val failure = streamer.stream(request())

        assertEquals(ReplyFailure.RATE_LIMITED, failure)
        assertEquals(MessageStatus.INTERRUPTED, reply.status)
        assertEquals(paragraph("Two lines"), reply.blocks)
        assertNull(reply.latencyMillis)
    }

    @Test
    fun aFailureBeforeAnyWordsLeavesTheReplyInterruptedAndEmpty() = runTest {
        fuelix.reply(GPT, failure = FuelixException.MissingKey())
        val streamer = streamer()

        assertEquals(ReplyFailure.NO_KEY, streamer.stream(request()))

        assertEquals(MessageStatus.INTERRUPTED, reply.status)
        assertEquals(emptyList(), reply.blocks)
        assertTrue(database.replyUpdates.isEmpty())
    }

    @Test
    fun everyFuelixFailureReadsAsItsReason() {
        assertEquals(
            listOf(
                ReplyFailure.NO_KEY,
                ReplyFailure.KEY_REFUSED,
                ReplyFailure.MODEL_UNAVAILABLE,
                ReplyFailure.MODEL_UNAVAILABLE,
                ReplyFailure.RATE_LIMITED,
                ReplyFailure.UNREACHABLE,
                ReplyFailure.FAILED,
                ReplyFailure.FAILED,
            ),
            listOf(
                FuelixException.MissingKey(),
                FuelixException.KeyRefused(),
                FuelixException.ModelUnavailable(status = 403),
                FuelixException.ModelUnavailable(status = 404),
                FuelixException.RateLimited(),
                FuelixException.Unreachable(cause = IllegalStateException("timeout")),
                FuelixException.Failed(status = 500),
                FuelixException.Failed(status = null),
            ).map { it.toReplyFailure() },
        )
    }

    @Test
    fun aWriteThatFailsFailsTheReplyAndLeavesItInterruptedRatherThanStreaming() = runTest {
        database.beforeReplyUpdate = { if (database.replyUpdates.size == 1) error("disk full") }
        val streamer = streamer()

        val failure = streamer.stream(request())

        assertEquals(ReplyFailure.FAILED, failure)
        assertEquals(MessageStatus.INTERRUPTED, reply.status)
        assertEquals(paragraph("Answer"), reply.blocks)
        assertEquals(2, fuelix.calls.single().sent)
    }

    @Test
    fun aStreamCancelledMidwayLeavesTheReplyInterruptedWithWhatItShowed() = runTest {
        val streamer = streamer()
        val stream = launch { streamer.stream(request()) }
        advanceTimeBy(PIECE_DELAY * 2)
        runCurrent()

        stream.cancel()
        runCurrent()

        assertEquals(MessageStatus.INTERRUPTED, reply.status)
        assertEquals(paragraph("Answer from"), reply.blocks)
        assertNull(reply.latencyMillis)
    }

    @Test
    fun aStreamCancelledWhileThinkingLeavesTheReplyInterruptedAndEmpty() = runTest {
        val streamer = streamer()
        val stream = launch { streamer.stream(request()) }
        advanceTimeBy(PIECE_DELAY / 2)

        stream.cancel()
        runCurrent()

        assertEquals(MessageStatus.INTERRUPTED, reply.status)
        assertEquals(emptyList(), reply.blocks)
    }

    @Test
    fun aStreamWhoseReplyIsGoneStopsOnTheWriteThatFindsNothingAndEndsQuietly() = runTest {
        val streamer = streamer()
        var failure: ReplyFailure? = ReplyFailure.FAILED
        val stream = launch { failure = streamer.stream(request()) }
        advanceTimeBy(PIECE_DELAY * 2)
        runCurrent()
        assertEquals(2, database.replyUpdates.size)

        database.conversationDao.delete(1)
        advanceUntilIdle()

        // The third piece finds no row to change, and the stream stops there.
        assertEquals(3, database.replyUpdates.size)
        assertEquals(3, fuelix.calls.single().sent)
        assertEquals((PIECE_DELAY * 3).inWholeMilliseconds, currentTime)
        assertTrue(stream.isCompleted && !stream.isCancelled)
        assertNull(failure)
        assertTrue(database.messageRows.value.isEmpty())
    }

    @Test
    fun aReplyWhoseRowIsGoneWhenItIsWholeEndsQuietly() = runTest {
        fuelix.hold(afterPieces = FakeFuelixClient.DEFAULT_PIECES)
        val streamer = streamer()
        val result = async { streamer.stream(request()) }
        advanceUntilIdle()
        assertEquals(MessageStatus.STREAMING, reply.status)

        database.conversationDao.delete(1)
        fuelix.release()
        advanceUntilIdle()

        assertNull(result.await())
        assertEquals(MessageStatus.DONE, database.replyUpdates.last().status)
        assertTrue(database.messageRows.value.isEmpty())
    }

    @Test
    fun aPairsSidesStreamAtOnceEachFromItsOwnModel() = runTest {
        val streamer = streamer()
        val result = async { streamer.stream(pair) }

        advanceTimeBy(PIECE_DELAY)
        assertEquals(listOf(MessageStatus.THINKING, MessageStatus.THINKING), pairStatuses())
        runCurrent()
        assertEquals(listOf(MessageStatus.STREAMING, MessageStatus.STREAMING), pairStatuses())

        advanceUntilIdle()
        assertNull(result.await())
        assertEquals(listOf(MessageStatus.DONE, MessageStatus.DONE), pairStatuses())
        assertEquals(paragraph("Answer from $SONNET."), database.message(FIRST_SIDE_ID).blocks)
        assertEquals(paragraph("Answer from $GPT."), database.message(SECOND_SIDE_ID).blocks)
        assertEquals(listOf(SONNET, GPT), fuelix.calls.map { it.model })
        // Side by side, not one after the other.
        assertEquals((PIECE_DELAY * FakeFuelixClient.DEFAULT_PIECES).inWholeMilliseconds, currentTime)
    }

    @Test
    fun eachSideOfAPairIsDoneOnItsOwnWithItsOwnLatency() = runTest {
        fuelix.reply(GPT, "Short ", "one.")
        val streamer = streamer()
        launch { streamer.stream(pair) }

        advanceTimeBy(PIECE_DELAY * 2)
        runCurrent()
        assertEquals(listOf(MessageStatus.STREAMING, MessageStatus.DONE), pairStatuses())
        assertEquals(paragraph("Short one."), database.message(SECOND_SIDE_ID).blocks)

        advanceUntilIdle()
        assertEquals(listOf(MessageStatus.DONE, MessageStatus.DONE), pairStatuses())
        assertEquals(listOf(150L, 50L), pairSides().map { it.latencyMillis })
    }

    @Test
    fun aPairReturnsTheFailureOfASideWhileTheOtherSideStillFinishes() = runTest {
        fuelix.reply(GPT, failure = FuelixException.ModelUnavailable(status = 403))
        val streamer = streamer()

        val failure = streamer.stream(pair)

        assertEquals(ReplyFailure.MODEL_UNAVAILABLE, failure)
        assertEquals(listOf(MessageStatus.DONE, MessageStatus.INTERRUPTED), pairStatuses())
        assertEquals(paragraph("Answer from $SONNET."), database.message(FIRST_SIDE_ID).blocks)
    }

    @Test
    fun whenBothSidesFailThePairReturnsTheFirstSidesFailure() = runTest {
        fuelix.reply(SONNET, "Partial", failure = FuelixException.RateLimited())
        fuelix.reply(GPT, failure = FuelixException.KeyRefused())
        val streamer = streamer()

        val failure = streamer.stream(pair)

        assertEquals(ReplyFailure.RATE_LIMITED, failure)
        assertEquals(listOf(MessageStatus.INTERRUPTED, MessageStatus.INTERRUPTED), pairStatuses())
        assertEquals(listOf(paragraph("Partial"), emptyList()), pairSides().map { it.blocks })
    }

    @Test
    fun aPairCancelledMidwayLeavesBothSidesInterruptedWithWhatTheyShowed() = runTest {
        fuelix.reply(GPT, "Most ", "likely ", "SSE.")
        val streamer = streamer()
        val stream = launch { streamer.stream(pair) }
        advanceTimeBy(PIECE_DELAY * 2)
        runCurrent()

        stream.cancel()
        runCurrent()

        assertEquals(listOf(MessageStatus.INTERRUPTED, MessageStatus.INTERRUPTED), pairStatuses())
        assertEquals(listOf(paragraph("Answer from"), paragraph("Most likely")), pairSides().map { it.blocks })
        assertEquals(listOf(null, null), pairSides().map { it.latencyMillis })
    }

    @Test
    fun aPairCancelledAfterOneSideIsDoneInterruptsOnlyTheOtherSide() = runTest {
        fuelix.reply(GPT, "Short.")
        val streamer = streamer()
        val stream = launch { streamer.stream(pair) }
        advanceTimeBy(PIECE_DELAY * 2)
        runCurrent()

        stream.cancel()
        runCurrent()

        assertEquals(listOf(MessageStatus.INTERRUPTED, MessageStatus.DONE), pairStatuses())
        assertEquals(0L, database.message(SECOND_SIDE_ID).latencyMillis)
    }

    @Test
    fun aPairWhoseRowsAreGoneStopsOnTheWritesThatFindNothingAndEndsQuietly() = runTest {
        val streamer = streamer()
        val result = async { streamer.stream(pair) }
        advanceTimeBy(PIECE_DELAY * 2)
        runCurrent()

        database.conversationDao.delete(1)
        advanceUntilIdle()

        assertNull(result.await())
        assertEquals(listOf(3, 3), fuelix.calls.map { it.sent })
        assertEquals((PIECE_DELAY * 3).inWholeMilliseconds, currentTime)
    }

    private fun pairSides(): List<MessageEntity> = listOf(FIRST_SIDE_ID, SECOND_SIDE_ID).map(database::message)

    private fun pairStatuses(): List<MessageStatus> = pairSides().map { it.status }

    private companion object {
        const val SONNET = "claude-sonnet-4-5-20250929"
        const val GPT = "gpt-5"
        const val REPLY_ID = 2L
        const val FIRST_SIDE_ID = 3L
        const val SECOND_SIDE_ID = 4L

        fun thinking(id: Long, modelId: String, pairSide: Int? = null) = MessageEntity(
            id = id,
            conversationId = 1,
            role = MessageRole.ASSISTANT,
            text = "",
            blocks = emptyList(),
            modelId = modelId,
            status = MessageStatus.THINKING,
            latencyMillis = null,
            createdAt = 0,
            pairSide = pairSide,
        )
    }
}
