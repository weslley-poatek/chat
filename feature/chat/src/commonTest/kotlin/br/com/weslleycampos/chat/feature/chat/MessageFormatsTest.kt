package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.ForkOrigin
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MessageFormatsTest {
    private fun reply(blocks: List<Block>, status: ReplyStatus = ReplyStatus.DONE) = Message.Assistant(
        id = 2,
        conversationId = 1,
        model = ModelCatalogue.default,
        blocks = blocks,
        status = status,
        latency = null,
        createdAt = Now,
    ).toUi(testTimes())

    @Test
    fun aMessagesCopyTextIsItsTextOrItsBlocksJoinedByABlankLineWithNumberedItems() {
        val user = Message.User(id = 1, conversationId = 1, text = "Why?\nIt lags.", createdAt = Now).toUi(testTimes())
        val blocks = listOf(
            Block.Paragraph("Two causes:"),
            Block.NumberedList(listOf("A stale value.", "A reassigned id.")),
            Block.Code(language = "ts", text = "const id = 1;\nclearTimeout(id);"),
        )

        assertEquals("Why?\nIt lags.", user.copyText())
        assertEquals(
            "Two causes:\n\n1. A stale value.\n2. A reassigned id.\n\nconst id = 1;\nclearTimeout(id);",
            reply(blocks).copyText(),
        )
        // An interrupted reply copies what it revealed, possibly nothing.
        assertEquals("Two causes:", reply(blocks.take(1), ReplyStatus.INTERRUPTED).copyText())
        assertEquals("", reply(emptyList(), ReplyStatus.INTERRUPTED).copyText())
    }

    @Test
    fun theForkBannerNamesTheSourceAndTheMessageNumberOrUntitled() {
        assertEquals(
            "forked from “useDebounce hook with leading edge” at message 2",
            forkBannerText(ForkOrigin(sourceTitle = "useDebounce hook with leading edge", messageNumber = 2)),
        )
        assertEquals(
            "forked from “untitled” at message 1",
            forkBannerText(ForkOrigin(sourceTitle = " ", messageNumber = 1)),
        )
    }

    @Test
    fun theReadAloudBarFillsByPointSixPercentATickAndIsFullAfter167Ticks() {
        assertEquals(100.milliseconds, ReadAloudTick)
        assertEquals(167, ReadAloudTicks)
        assertEquals(0f, readAloudProgress(0))
        assertEquals(0.006f, readAloudProgress(1))
        assertEquals(0.3f, readAloudProgress(50))
        assertEquals(0.996f, readAloudProgress(ReadAloudTicks - 1))
        assertEquals(1f, readAloudProgress(ReadAloudTicks))
        assertEquals(1f, readAloudProgress(ReadAloudTicks + 50))
        assertEquals(0f, readAloudProgress(-1))
    }

    @Test
    fun theReadAloudTimeTracksTheBarsOwnTicksToTheNearestSecond() {
        assertEquals("0:00 / 0:17", readAloudLabel(0))
        assertEquals("0:00 / 0:17", readAloudLabel(4))
        // 0.5 s rounds up, as the prototype's `Math.round`.
        assertEquals("0:01 / 0:17", readAloudLabel(5))
        assertEquals("0:08 / 0:17", readAloudLabel(84))
        assertEquals("0:17 / 0:17", readAloudLabel(ReadAloudTicks))
        assertEquals("0:17 / 0:17", readAloudLabel(ReadAloudTicks + 50))
        assertEquals("0:00 / 0:17", readAloudLabel(-1))
        assertEquals("1:15", formatReadTime(75.seconds))
        assertEquals("1:00", formatReadTime(59500.milliseconds))
    }

    @Test
    fun theReadAloudBarIsFullOnceItHasPlayedEveryTick() {
        val reading = ReadAloudUi(messageId = 2, ticks = 70)

        assertEquals(0.42f to "0:07 / 0:17", reading.progress to reading.label)
        assertTrue(reading.playing)
        assertFalse(reading.finished)
        assertTrue(reading.copy(ticks = ReadAloudTicks, playing = false).finished)
    }
}
