package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.data.chat.models.Block

/**
 * The design prototype's `REPLIES`: the three answers every model gives, in turn. A conversation's
 * first reply is the first one, its second reply the second, its fourth the first again.
 *
 * Its `REPLY_ALT` are the [alternatives]: a compare pair's second side answers with the one at the
 * same place, while its first side answers with the reply. A pair counts as one reply in the turn.
 */
internal object CannedReplies {
    val replies: List<List<Block>> = listOf(
        listOf(
            Block.Paragraph("Two likely causes, in order of probability:"),
            Block.NumberedList(
                listOf(
                    "The effect closes over a stale value because delay is missing from the dependency array.",
                    "The timeout id is reassigned before the previous one is cleared, " +
                        "so cleanup clears the wrong timer.",
                ),
            ),
            Block.Paragraph("Minimal fix:"),
            Block.Code(
                language = "ts",
                text = "useEffect(() => {\n" +
                    "  const id = setTimeout(() => setDebounced(value), delay);\n" +
                    "  return () => clearTimeout(id);\n" +
                    "}, [value, delay]);",
            ),
            Block.Paragraph("If it still reads stale, log value inside the callback and check the render count."),
        ),
        listOf(
            Block.Paragraph("Shell equivalent, no dependencies:"),
            Block.Code(
                language = "bash",
                text = "find . -name \"*.log\" -mtime +7 -print0 \\\n" +
                    "  | xargs -0 rm -v",
            ),
            Block.Paragraph("-print0 / -0 handles paths with spaces. Drop -v if the output is noise."),
        ),
        listOf(
            Block.Code(
                language = "python",
                text = "from functools import lru_cache\n" +
                    "\n" +
                    "@lru_cache(maxsize=None)\n" +
                    "def fib(n: int) -> int:\n" +
                    "    return n if n < 2 else fib(n - 1) + fib(n - 2)",
            ),
            Block.Paragraph("Recursion depth is the limit here (~1000 frames). For larger n, iterate."),
        ),
    )

    /** The prototype's `REPLY_ALT`, one for each of [replies], in the same order. */
    val alternatives: List<List<Block>> = listOf(
        listOf(
            Block.Paragraph(
                "Most likely a stale closure. Include every value the effect reads in its dependency array, " +
                    "and return the cleanup:",
            ),
            Block.Code(
                language = "ts",
                text = "useEffect(() => {\n" +
                    "  const t = setTimeout(() => setDebounced(value), delay);\n" +
                    "  return () => clearTimeout(t);\n" +
                    "}, [value, delay]);",
            ),
        ),
        listOf(
            Block.Code(language = "bash", text = "find . -name \"*.log\" -mtime +7 -delete"),
            Block.Paragraph("-delete is a GNU and BSD find extension; it avoids the xargs pipe entirely."),
        ),
        listOf(
            Block.Code(
                language = "python",
                text = "def fib(n: int) -> int:\n" +
                    "    a, b = 0, 1\n" +
                    "    for _ in range(n):\n" +
                    "        a, b = b, a + b\n" +
                    "    return a",
            ),
            Block.Paragraph("Iterative, O(n), no recursion limit."),
        ),
    )

    /** The reply for a conversation that already has [repliesSoFar] replies, a pair counting as one. */
    fun next(repliesSoFar: Int): List<Block> = replies[repliesSoFar.mod(replies.size)]

    /** A compare pair's second side, beside [next] on its first side: the alternative at the same place. */
    fun alternative(repliesSoFar: Int): List<Block> = alternatives[repliesSoFar.mod(alternatives.size)]
}
