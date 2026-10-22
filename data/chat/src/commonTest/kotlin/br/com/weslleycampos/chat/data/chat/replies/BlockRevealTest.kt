package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.data.chat.fakes.FixedRandom
import br.com.weslleycampos.chat.data.chat.models.Block
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlockRevealTest {
    private val reply = listOf(
        Block.Paragraph("Minimal fix:"),
        Block.Code(language = "ts", text = "useEffect(() => {});"),
        Block.NumberedList(listOf("one", "two")),
    )

    @Test
    fun atTheSlowestPaceTextGrowsByTwoAndCodeBySixCharactersATick() {
        val reveal = BlockReveal(reply)
        val ticks = generateSequence { if (reveal.isDone) null else reveal.advance(FixedRandom()) }.toList()

        assertEquals(
            listOf(
                listOf(Block.Paragraph("Mi")),
                listOf(Block.Paragraph("Mini")),
                listOf(Block.Paragraph("Minima")),
                listOf(Block.Paragraph("Minimal ")),
                listOf(Block.Paragraph("Minimal fi")),
                listOf(Block.Paragraph("Minimal fix:")),
                listOf(reply[0], Block.Code(language = "ts", text = "useEff")),
                listOf(reply[0], Block.Code(language = "ts", text = "useEffect(()")),
                listOf(reply[0], Block.Code(language = "ts", text = "useEffect(() => {}")),
                listOf(reply[0], reply[1]),
                listOf(reply[0], reply[1], Block.NumberedList(listOf("on"))),
                listOf(reply[0], reply[1], Block.NumberedList(listOf("one"))),
                listOf(reply[0], reply[1], Block.NumberedList(listOf("one", "tw"))),
                listOf(reply[0], reply[1], reply[2]),
            ).map { it.toString() },
            ticks.map { it.toString() },
        )
    }

    @Test
    fun atTheFastestPaceTextGrowsBySevenAndCodeByFifteenCharactersATick() {
        val reveal = BlockReveal(reply)
        val fastest = FixedRandom { until -> until - 1 }

        assertEquals(listOf(Block.Paragraph("Minimal")), reveal.advance(fastest))
        assertEquals(listOf(reply[0]), reveal.advance(fastest))
        assertEquals(listOf(reply[0], Block.Code(language = "ts", text = "useEffect(() =>")), reveal.advance(fastest))
    }

    @Test
    fun everyTickShowsTheFinishedBlocksWholeAndThenAPrefixOfTheCurrentOne() {
        val target = CannedReplies.replies[0]
        val reveal = BlockReveal(target)
        val random = Random(seed = 7)
        var previous = emptyList<Block>()

        while (!reveal.isDone) {
            val visible = reveal.advance(random)
            assertEquals(target.take(visible.size - 1), visible.dropLast(1))
            assertTrue(visible.last().isPrefixOf(target[visible.size - 1]), "${visible.last()}")
            assertTrue(visible.size >= previous.size && visible.size <= previous.size + 1)
            previous = visible
        }
        assertEquals(target, previous)
        assertFailsWith<IllegalStateException> { reveal.advance(random) }
    }

    @Test
    fun aListRevealsItsItemsOneAfterAnotherCountingOneForEachBreak() {
        val list = Block.NumberedList(listOf("abc", "de"))

        assertEquals(7, list.revealLength)
        assertEquals(Block.NumberedList(emptyList()), list.revealedTo(0))
        assertEquals(Block.NumberedList(listOf("abc")), list.revealedTo(3))
        assertEquals(Block.NumberedList(listOf("abc")), list.revealedTo(4))
        assertEquals(Block.NumberedList(listOf("abc", "d")), list.revealedTo(5))
        assertEquals(list, list.revealedTo(6))
        assertEquals(list, list.revealedTo(7))
    }

    private fun Block.isPrefixOf(whole: Block): Boolean = when {
        this is Block.Paragraph && whole is Block.Paragraph -> whole.text.startsWith(text)
        this is Block.Code && whole is Block.Code -> language == whole.language && whole.text.startsWith(text)
        this is Block.NumberedList && whole is Block.NumberedList ->
            items.size <= whole.items.size &&
                items.dropLast(1) == whole.items.take(items.size - 1) &&
                (items.isEmpty() || whole.items[items.size - 1].startsWith(items.last()))
        else -> false
    }
}
