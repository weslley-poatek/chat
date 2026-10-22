package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.data.chat.models.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CannedRepliesTest {
    @Test
    fun theRepliesAreThePrototypesThreeScriptsBlockByBlock() {
        val shapes = CannedReplies.replies.map { reply ->
            reply.map { block ->
                when (block) {
                    is Block.Paragraph -> "p"
                    is Block.Code -> "code ${block.language}"
                    is Block.NumberedList -> "list ${block.items.size}"
                }
            }
        }

        assertEquals(
            listOf(
                listOf("p", "list 2", "p", "code ts", "p"),
                listOf("p", "code bash", "p"),
                listOf("code python", "p"),
            ),
            shapes,
        )
        // The prototype's blockLen of every block: a check on every character of the copy.
        assertEquals(
            listOf(listOf(43, 189, 12, 129, 82), listOf(34, 59, 71), listOf(132, 72)),
            CannedReplies.replies.map { reply -> reply.map { it.revealLength } },
        )
    }

    @Test
    fun theTextIsThePrototypesToTheCharacter() {
        assertEquals(
            Block.Code(language = "bash", text = "find . -name \"*.log\" -mtime +7 -print0 \\\n  | xargs -0 rm -v"),
            CannedReplies.replies[1][1],
        )
        assertEquals(
            Block.Paragraph("Recursion depth is the limit here (~1000 frames). For larger n, iterate."),
            CannedReplies.replies[2][1],
        )
    }

    @Test
    fun aConversationsRepliesRotateThroughTheThreeInOrder() {
        val picked = (0..6).map { CannedReplies.next(repliesSoFar = it) }

        listOf(0, 1, 2, 0, 1, 2, 0).forEachIndexed { reply, script ->
            assertSame(CannedReplies.replies[script], picked[reply])
        }
    }
}
