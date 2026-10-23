package br.com.weslleycampos.chat.feature.chat

import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.CompareSide
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Compare mode's texts, a pair as the thread draws it, and the carousel's geometry. */
class CompareFormatsTest {
    private val sonnet = ModelCatalogue.default

    private fun pair(
        first: ReplyStatus,
        second: ReplyStatus,
        firstBlocks: List<Block> = ReplyBlocks,
        secondBlocks: List<Block> = AltBlocks,
    ) = Message.ComparePair(
        id = 3,
        conversationId = 1,
        sides = listOf(
            CompareSide(
                id = 3,
                model = sonnet,
                blocks = firstBlocks,
                status = first,
                latency = ReplyLatency.takeIf { first == ReplyStatus.DONE },
            ),
            CompareSide(
                id = 4,
                model = Gpt5,
                blocks = secondBlocks,
                status = second,
                latency = AltLatency.takeIf { second == ReplyStatus.DONE },
            ),
        ),
        createdAt = Now,
    )

    private val BlockUi.caret: Boolean
        get() = when (this) {
            is BlockUi.Paragraph -> caret
            is BlockUi.Code -> caret
            is BlockUi.NumberedList -> false
        }

    // Texts

    @Test
    fun theChipNamesBothModelsOnlyOnceASecondIsPicked() {
        assertEquals("sonnet-4.5 ⇄ gpt-5", chipLabel(ModelChoice(sonnet, compareOn = true, compareWith = Gpt5)))
        assertEquals(
            "gemini-2.5-pro ⇄ llama-4",
            chipLabel(ModelChoice(Gemini, compareOn = true, compareWith = Llama)),
        )
        assertEquals("Claude Sonnet 4.5", chipLabel(ModelChoice(sonnet, compareOn = true)))
        assertEquals("Claude Sonnet 4.5", chipLabel(ModelChoice(sonnet, compareWith = Gpt5)))
        assertEquals("sonnet-4.5 ⇄ gpt-5", compareLabel(sonnet, Gpt5))
    }

    @Test
    fun theComposerAsksBothModelsWhileComparingElseMessagesTheOne() {
        assertEquals("message sonnet-4.5…", composerPlaceholder(ModelChoice(sonnet)))
        assertEquals("message sonnet-4.5…", composerPlaceholder(ModelChoice(sonnet, compareOn = true)))
        assertEquals("message gpt-5…", composerPlaceholder(ModelChoice(Terse)))
        assertEquals(
            "ask sonnet-4.5 and gpt-5…",
            composerPlaceholder(ModelChoice(sonnet, compareOn = true, compareWith = Gpt5)),
        )
        val comparing = ChatUiState(choice = ModelChoice(sonnet, compareOn = true, compareWith = Gpt5))
        assertEquals("ask sonnet-4.5 and gpt-5…", comparing.composerPlaceholder)
    }

    @Test
    fun aPairsMetaIsBothShortNamesAndTheTimeWithoutLatency() {
        val done = pair(ReplyStatus.DONE, ReplyStatus.DONE).toUi(time = "14:05")

        assertEquals("sonnet-4.5 ⇄ gpt-5 · 14:05", compareMeta(done))
    }

    @Test
    fun aCardsHeaderIsItsModelThenItsLatencyOnceDoneOrInterrupted() {
        val (streaming, done) = pair(ReplyStatus.STREAMING, ReplyStatus.DONE).toUi(time = "14:05").sides
        val interrupted = pair(ReplyStatus.INTERRUPTED, ReplyStatus.DONE).toUi(time = "14:05").sides.first()

        assertEquals("Claude Sonnet 4.5", compareSideLabel(streaming, interruptedLabel = "interrupted"))
        assertEquals("GPT-5 · 1.9s", compareSideLabel(done, interruptedLabel = "interrupted"))
        assertEquals(
            "Claude Sonnet 4.5 · interrupted",
            compareSideLabel(interrupted, interruptedLabel = "interrupted"),
        )
    }

    // A pair as the thread draws it

    @Test
    fun aPairsCardsAreItsSidesInOrderEachInItsOwnModelsPalette() {
        val done = pair(ReplyStatus.DONE, ReplyStatus.DONE).toUi(time = "14:05")

        assertEquals(3L, done.id)
        assertEquals("14:05", done.time)
        assertEquals(listOf(3L, 4L), done.sides.map { it.id })
        assertEquals(listOf(0, 1), done.sides.map { it.index })
        assertEquals(listOf("Claude Sonnet 4.5", "GPT-5"), done.sides.map { it.modelName })
        assertEquals(listOf("sonnet-4.5", "gpt-5"), done.sides.map { it.modelShortName })
        assertEquals(listOf(ChatPalette.SONNET, ChatPalette.GPT5), done.sides.map { it.palette })
        assertEquals(listOf("0.8s", "1.9s"), done.sides.map { it.latency })
        assertTrue(done.sides.all { it.done && !it.inProgress && !it.thinking && !it.interrupted })
        assertTrue(done.sides.flatMap { it.blocks }.none { it.caret })
        assertFalse(done.inProgress)
        // Through the thread's mapping, too.
        assertEquals(done.copy(time = "14:02"), pair(ReplyStatus.DONE, ReplyStatus.DONE).toUi(testTimes()))
    }

    @Test
    fun aThinkingCardHasTheLoneCaretAndAStreamingOneItsLastParagraphsButNeverInCode() {
        val thinking = pair(ReplyStatus.THINKING, ReplyStatus.THINKING, emptyList(), emptyList()).toUi(time = "14:05")
        assertTrue(thinking.sides.all { it.thinking && it.inProgress && !it.done && it.latency == null })
        assertTrue(thinking.inProgress)

        val streaming = pair(
            first = ReplyStatus.STREAMING,
            second = ReplyStatus.STREAMING,
            firstBlocks = ReplyBlocks.take(1),
            secondBlocks = AltBlocks,
        ).toUi(time = "14:05")
        val (paragraphs, code) = streaming.sides
        assertEquals(listOf(true), paragraphs.blocks.map { it.caret })
        // The second card's last block is code, which the prototype's cards draw without a caret.
        assertIs<BlockUi.Code>(code.blocks.last())
        assertEquals(listOf(false, false), code.blocks.map { it.caret })
        assertTrue(streaming.sides.none { it.thinking || it.done })
        assertEquals(listOf(null, null), streaming.sides.map { it.latency })
    }

    @Test
    fun aCardDoneBesideOneOnItsWayKeepsThePairInProgress() {
        val pair = pair(ReplyStatus.STREAMING, ReplyStatus.DONE)
        val ui = pair.toUi(time = "14:05")

        assertEquals(listOf(false, true), ui.sides.map { it.done })
        assertTrue(ui.inProgress)
        assertTrue(listOf(pair).replyInProgress())
        assertFalse(listOf(pair(ReplyStatus.DONE, ReplyStatus.INTERRUPTED)).replyInProgress())
        assertTrue(listOf(pair(ReplyStatus.DONE, ReplyStatus.THINKING, secondBlocks = emptyList())).replyInProgress())
    }

    @Test
    fun anInterruptedCardShowsWhatItRevealedWithoutCaretOrContinue() {
        val ui = pair(ReplyStatus.INTERRUPTED, ReplyStatus.DONE, firstBlocks = ReplyBlocks.take(2)).toUi(time = "14:05")
        val interrupted = ui.sides.first()

        assertTrue(interrupted.interrupted)
        assertFalse(interrupted.done || interrupted.inProgress || interrupted.thinking)
        assertNull(interrupted.latency)
        assertEquals(2, interrupted.blocks.size)
        assertTrue(interrupted.blocks.none { it.caret })
    }

    @Test
    fun aPairIsNeverSelectableAndItsCardsCodeIsFoundForCopy() {
        val ui = pair(ReplyStatus.DONE, ReplyStatus.DONE).toUi(time = "14:05")
        val messages = listOf<MessageUi>(MessageUi.User(id = 2, time = "14:05", text = "Why?"), ui)

        assertFalse(ui.selectable)
        val firstCode = BlockKey(messageId = 3, index = ReplyCodeIndex)
        val secondCode = BlockKey(messageId = 4, index = 1)
        assertEquals("return () => clearTimeout(id);", messages.codeBlock(firstCode)?.text)
        assertEquals("}, [value, delay]);", messages.codeBlock(secondCode)?.text)
        assertNull(messages.codeBlock(BlockKey(messageId = 4, index = 0)))
        assertNull(messages.codeBlock(BlockKey(messageId = 5, index = 1)))
    }

    @Test
    fun aPairsCopyTextIsBothCardsInOrder() {
        val ui = pair(
            first = ReplyStatus.DONE,
            second = ReplyStatus.DONE,
            firstBlocks = listOf(Block.Paragraph("First.")),
            secondBlocks = listOf(Block.Paragraph("Second."), Block.Code(language = "ts", text = "x();")),
        ).toUi(time = "14:05")

        assertEquals("First.\n\nSecond.\n\nx();", ui.copyText())
    }

    // The carousel

    @Test
    fun aCardIsEightySixPercentOfTheCarouselInsideItsGuttersSoTheOtherPeeks() {
        val width = compareCardWidth(viewport = 402.dp, gutter = 16.dp)

        assertEquals(318.2f, width.value, absoluteTolerance = 0.01f)
        // Two cards and the 8 gap overflow the 370 between the gutters, so the second peeks.
        assertEquals(59.8f, (402.dp - 16.dp - width - 8.dp).value, absoluteTolerance = 0.01f)
        assertEquals(0f, compareCardWidth(viewport = 20.dp, gutter = 16.dp).value)
    }

    @Test
    fun theCarouselSettlesOnTheNearerCardUnlessAFlingGoesOn() {
        val max = 326f
        val threshold = 1000f

        assertEquals(0f, compareSnapTarget(current = 100f, max = max, velocity = 0f, flingThreshold = threshold))
        assertEquals(max, compareSnapTarget(current = 163f, max = max, velocity = 0f, flingThreshold = threshold))
        assertEquals(max, compareSnapTarget(current = 20f, max = max, velocity = 1500f, flingThreshold = threshold))
        assertEquals(0f, compareSnapTarget(current = 300f, max = max, velocity = -1500f, flingThreshold = threshold))
        // A slow fling settles by position.
        assertEquals(max, compareSnapTarget(current = 250f, max = max, velocity = -500f, flingThreshold = threshold))
    }
}
