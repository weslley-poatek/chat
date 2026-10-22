package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TypeScript = "useEffect(() => {\n" +
    "  const id = setTimeout(() => setDebounced(value), delay);\n" +
    "  return () => clearTimeout(id);\n" +
    "}, [value, delay]);"

private const val Bash = "find . -name \"*.log\" -mtime +7 -print0 \\\n" +
    "  | xargs -0 rm -v"

private const val Python = "from functools import lru_cache\n" +
    "\n" +
    "@lru_cache(maxsize=None)\n" +
    "def fib(n: int) -> int:\n" +
    "    return n if n < 2 else fib(n - 1) + fib(n - 2)"

class CodeTokensTest {
    private fun kinds(code: String) = tokenize(code).map { it.text to it.kind }

    @Test
    fun theTokensJoinBackIntoTheCode() {
        for (code in listOf(TypeScript, Bash, Python, "", "\"unterminated", "a\n\n\tb")) {
            assertEquals(code, tokenize(code).joinToString("") { it.text })
        }
    }

    @Test
    fun neighboursOfOneKindAreOneRun() {
        val tokens = tokenize(TypeScript)

        tokens.zipWithNext().forEach { (left, right) -> assertTrue(left.kind != right.kind, "$left then $right") }
    }

    @Test
    fun keywordsFunctionsAndPlainWords() {
        assertEquals(
            listOf(
                "  " to null,
                "const" to TokenKind.Keyword,
                " id = " to null,
                "setTimeout" to TokenKind.Function,
                "(() => " to null,
                "setDebounced" to TokenKind.Function,
                "(value), delay);" to null,
            ),
            kinds("  const id = setTimeout(() => setDebounced(value), delay);"),
        )
    }

    @Test
    fun commentsRunToTheEndOfTheLineAndWinOverWhatIsInThem() {
        assertEquals(
            listOf(
                "x = " to null,
                "42" to TokenKind.Number,
                " " to null,
                "// \"not a string\" return" to TokenKind.Comment,
                "\n" to null,
                "# python" to TokenKind.Comment,
            ),
            kinds("x = 42 // \"not a string\" return\n# python"),
        )
    }

    @Test
    fun stringsInEveryQuoteAndOneStillBeingTyped() {
        assertEquals(
            listOf(
                "\"a\"" to TokenKind.String,
                " + " to null,
                "'b'" to TokenKind.String,
                " + " to null,
                "`c\nd`" to TokenKind.String,
                " + " to null,
                "\"stream" to TokenKind.String,
                "\ning" to null,
            ),
            kinds("\"a\" + 'b' + `c\nd` + \"stream\ning"),
        )
    }

    @Test
    fun numbersAndCapitalisedTypes() {
        assertEquals(
            listOf(
                "3.14" to TokenKind.Number,
                " " to null,
                "ReturnType" to TokenKind.Type,
                "<" to null,
                "None" to TokenKind.Type,
                "> v2" to null,
            ),
            kinds("3.14 ReturnType<None> v2"),
        )
    }

    @Test
    fun theShellSnippetColoursItsCommandsAndFlags() {
        val tokens = kinds(Bash)

        assertEquals("find" to TokenKind.Keyword, tokens.first())
        assertTrue("\"*.log\"" to TokenKind.String in tokens)
        assertTrue("7" to TokenKind.Number in tokens)
        assertTrue("xargs" to TokenKind.Keyword in tokens)
        assertTrue("rm" to TokenKind.Keyword in tokens)
    }

    @Test
    fun everyKindTakesItsSyntaxColourAndPlainTextTheBlocks() {
        for (palette in ChatPalette.entries) {
            for (dark in listOf(false, true)) {
                val syntax = palette.colors(dark).syntax
                val expected = listOf(
                    syntax.comment,
                    syntax.string,
                    syntax.number,
                    syntax.type,
                    syntax.keyword,
                    syntax.function,
                )

                assertEquals(expected, TokenKind.entries.map { it.color(syntax) })
                val highlighted = highlighted(tokenize(Python), syntax)
                assertEquals(Python, highlighted.text)
                assertEquals(
                    tokenize(Python).count { it.kind != null },
                    highlighted.spanStyles.size,
                )
            }
        }
    }
}
