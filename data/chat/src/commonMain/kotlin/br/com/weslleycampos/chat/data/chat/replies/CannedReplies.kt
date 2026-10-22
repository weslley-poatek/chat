package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.data.chat.models.Block

/**
 * The design prototype's `REPLIES`: the three answers every model gives, in turn. A conversation's
 * first reply is the first one, its second reply the second, its fourth the first again.
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

    /** The reply for a conversation that already has [repliesSoFar] replies. */
    fun next(repliesSoFar: Int): List<Block> = replies[repliesSoFar.mod(replies.size)]
}
