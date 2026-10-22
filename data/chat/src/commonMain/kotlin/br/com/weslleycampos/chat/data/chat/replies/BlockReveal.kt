package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.data.chat.models.Block
import kotlin.random.Random

/**
 * Reveals a reply a few characters per tick, block after block, as the prototype's `startStream`
 * does with its cursor: a code block grows by 6–15 characters a tick and any other block by 2–7.
 * A finished block stays whole, and the next one appears on the following tick.
 */
internal class BlockReveal(private val blocks: List<Block>) {
    init {
        require(blocks.isNotEmpty()) { "A reply has at least one block" }
    }

    private var blockIndex = 0
    private var revealed = 0

    /** True once the last block is whole. */
    var isDone: Boolean = false
        private set

    /** One tick: reveals more of the current block and returns every block visible after it. */
    fun advance(random: Random): List<Block> {
        check(!isDone) { "The reply is already revealed" }
        val block = blocks[blockIndex]
        val length = block.revealLength
        revealed = minOf(revealed + block.charactersPerTick(random), length)
        val visible = blocks.subList(0, blockIndex) + block.revealedTo(revealed)
        if (revealed >= length) {
            if (blockIndex < blocks.lastIndex) {
                blockIndex++
                revealed = 0
            } else {
                isDone = true
            }
        }
        return visible
    }
}

private const val CODE_MIN_CHARACTERS = 6
private const val CODE_EXTRA_CHARACTERS = 10
private const val TEXT_MIN_CHARACTERS = 2
private const val TEXT_EXTRA_CHARACTERS = 6

private fun Block.charactersPerTick(random: Random): Int = when (this) {
    is Block.Code -> CODE_MIN_CHARACTERS + random.nextInt(CODE_EXTRA_CHARACTERS)
    is Block.Paragraph, is Block.NumberedList -> TEXT_MIN_CHARACTERS + random.nextInt(TEXT_EXTRA_CHARACTERS)
}

/** The prototype's `blockLen`: the text's length, or for a list every item's length plus one. */
internal val Block.revealLength: Int
    get() = when (this) {
        is Block.Paragraph -> text.length
        is Block.Code -> text.length
        is Block.NumberedList -> items.sumOf { it.length + 1 }
    }

/**
 * The prototype's `partialBlock`: the first [characters] of the block. A list counts each item's
 * length plus one, so its items appear one after another.
 */
internal fun Block.revealedTo(characters: Int): Block = when (this) {
    is Block.Paragraph -> copy(text = text.take(characters))
    is Block.Code -> copy(text = text.take(characters))
    is Block.NumberedList -> {
        val shown = mutableListOf<String>()
        var left = characters
        for (item in items) {
            if (left <= 0) break
            shown += item.take(left)
            left -= item.length + 1
        }
        Block.NumberedList(shown)
    }
}
