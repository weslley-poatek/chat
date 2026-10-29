package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.data.chat.models.Block

/**
 * A model's Markdown as the thread's blocks, and back for the next request:
 *
 * - a fenced code block (```` ```kotlin ````) is a [Block.Code] with its language, one still open
 *   while the reply streams included;
 * - a run of numbered lines ("1. ", "2) ") is a [Block.NumberedList], blank lines between its items
 *   included, as models often write a list;
 * - any other run of lines up to a blank one is a [Block.Paragraph], its heading marks ("## ")
 *   dropped and a bullet ("- ", "* ") read as "• ".
 *
 * Inline marks such as `**bold**` stay as they are: the thread shows text, not Markdown.
 */
internal object MarkdownBlocks {
    private const val FENCE = "```"
    private val NumberedItem = Regex("""^\s*\d+[.)]\s+(.*)$""")
    private val Bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val Heading = Regex("""^#{1,6}\s+(.*)$""")

    /** The blocks [markdown] reads as, none for blank text. */
    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        val list = mutableListOf<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) blocks += Block.Paragraph(paragraph.joinToString("\n"))
            if (list.isNotEmpty()) blocks += Block.NumberedList(list.toList())
            paragraph.clear()
            list.clear()
        }
        val lines = markdown.replace("\r\n", "\n").lines()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            val item = NumberedItem.matchEntire(line)
            when {
                trimmed.startsWith(FENCE) -> {
                    flush()
                    val end = lines.fenceEnd(index)
                    val code = lines.subList(index + 1, end).joinToString("\n")
                    blocks += Block.Code(language = trimmed.removePrefix(FENCE).trim(), text = code)
                    index = end
                }
                // A blank line inside a list, before its next item, keeps the list going.
                trimmed.isEmpty() -> if (list.isEmpty() || !lines.nextItemFollows(index)) flush()
                item != null -> {
                    if (paragraph.isNotEmpty()) flush()
                    list += item.groupValues[1].trim()
                }
                else -> {
                    if (list.isNotEmpty()) flush()
                    paragraph += plain(line)
                }
            }
            index++
        }
        flush()
        return blocks
    }

    /** [blocks] written back as Markdown, for the model to read its earlier replies. */
    fun write(blocks: List<Block>): String = blocks.joinToString("\n\n") { block ->
        when (block) {
            is Block.Paragraph -> block.text
            is Block.Code -> "$FENCE${block.language}\n${block.text}\n$FENCE"
            is Block.NumberedList -> block.items.mapIndexed { index, item -> "${index + 1}. $item" }.joinToString("\n")
        }
    }

    /** The index of the fence that closes the one at [start], or the end of the lines while it is still open. */
    private fun List<String>.fenceEnd(start: Int): Int =
        (start + 1 until size).firstOrNull { this[it].trim().startsWith(FENCE) } ?: size

    /** Whether the first line after [index] that is not blank is a numbered item. */
    private fun List<String>.nextItemFollows(index: Int): Boolean =
        drop(index + 1).firstOrNull { it.isNotBlank() }?.let { NumberedItem.matches(it) } == true

    private fun plain(line: String): String {
        Heading.find(line.trim())?.let { return it.groupValues[1] }
        Bullet.find(line)?.let { return "${it.groupValues[1]}• ${it.groupValues[2]}" }
        return line.trimEnd()
    }
}
