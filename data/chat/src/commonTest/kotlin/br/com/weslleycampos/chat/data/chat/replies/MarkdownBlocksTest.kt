package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.data.chat.models.Block
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownBlocksTest {
    @Test
    fun linesUpToABlankOneAreOneParagraph() {
        assertEquals(
            listOf(Block.Paragraph("Minimal fix:\nkeep the timer in a ref."), Block.Paragraph("That is all.")),
            MarkdownBlocks.parse("Minimal fix:\nkeep the timer in a ref.  \n\n\nThat is all."),
        )
    }

    @Test
    fun blankTextHasNoBlocks() {
        assertEquals(emptyList(), MarkdownBlocks.parse(""))
        assertEquals(emptyList(), MarkdownBlocks.parse(" \n\n\t\n"))
    }

    @Test
    fun aFencedCodeBlockIsCodeWithItsLanguageItsBlankLinesAndIndentation() {
        val markdown = "Before:\n```ts\nfunction a() {\n\n  return 1;\n}\n```\nAfter."

        assertEquals(
            listOf(
                Block.Paragraph("Before:"),
                Block.Code(language = "ts", text = "function a() {\n\n  return 1;\n}"),
                Block.Paragraph("After."),
            ),
            MarkdownBlocks.parse(markdown),
        )
        assertEquals(listOf(Block.Code(language = "", text = "ls -la")), MarkdownBlocks.parse("```\nls -la\n```"))
    }

    @Test
    fun aCodeBlockStillOpenWhileTheReplyStreamsIsCodeSoFar() {
        assertEquals(
            listOf(Block.Paragraph("Here:"), Block.Code(language = "python", text = "import os\nprint(")),
            MarkdownBlocks.parse("Here:\n```python\nimport os\nprint("),
        )
        assertEquals(
            listOf(Block.Paragraph("Here:"), Block.Code(language = "py", text = "")),
            MarkdownBlocks.parse("Here:\n```py"),
        )
    }

    @Test
    fun aRunOfNumberedLinesIsANumberedListApartFromTheParagraphsAroundIt() {
        assertEquals(
            listOf(
                Block.Paragraph("Steps:"),
                Block.NumberedList(listOf("Add a leading flag.", "Keep the timer in a ref.", "Clear it on unmount.")),
                Block.Paragraph("Done."),
            ),
            MarkdownBlocks.parse(
                "Steps:\n1. Add a leading flag.\n2) Keep the timer in a ref.\n  3.  Clear it on unmount.\nDone.",
            ),
        )
    }

    @Test
    fun aNumberedListWithBlankLinesBetweenItsItemsIsOneList() {
        assertEquals(
            listOf(
                Block.NumberedList(listOf("Add a leading flag.", "Keep the timer in a ref.")),
                Block.Paragraph("Done."),
            ),
            MarkdownBlocks.parse("1. Add a leading flag.\n\n2. Keep the timer in a ref.\n\nDone."),
        )
    }

    @Test
    fun headingMarksAreDroppedAndBulletsReadAsDots() {
        assertEquals(
            listOf(Block.Paragraph("Setup"), Block.Paragraph("• one\n• two\n  • nested")),
            MarkdownBlocks.parse("## Setup\n\n- one\n* two\n  + nested"),
        )
        assertEquals(listOf(Block.Paragraph("Title")), MarkdownBlocks.parse("###### Title"))
    }

    @Test
    fun inlineMarksStayAsTheyAre() {
        assertEquals(
            listOf(Block.Paragraph("**Use SSE** for a `dashboard`, #1 choice")),
            MarkdownBlocks.parse("**Use SSE** for a `dashboard`, #1 choice"),
        )
    }

    @Test
    fun windowsLineEndingsReadAsUnixOnes() {
        assertEquals(
            listOf(Block.Paragraph("one\ntwo"), Block.Code(language = "sh", text = "echo hi")),
            MarkdownBlocks.parse("one\r\ntwo\r\n\r\n```sh\r\necho hi\r\n```"),
        )
    }

    @Test
    fun blocksAreWrittenBackAsMarkdownOneBlankLineApart() {
        val blocks = listOf(
            Block.Paragraph("Minimal fix:\n• keep the timer in a ref"),
            Block.Code(language = "kotlin", text = "fun a() {}\n\nfun b() {}"),
            Block.NumberedList(listOf("one", "two")),
            Block.Paragraph("That is all."),
        )

        val markdown = MarkdownBlocks.write(blocks)

        assertEquals(
            "Minimal fix:\n• keep the timer in a ref\n\n```kotlin\nfun a() {}\n\nfun b() {}\n```\n\n" +
                "1. one\n2. two\n\nThat is all.",
            markdown,
        )
        assertEquals(blocks, MarkdownBlocks.parse(markdown))
        assertEquals("", MarkdownBlocks.write(emptyList()))
    }
}
