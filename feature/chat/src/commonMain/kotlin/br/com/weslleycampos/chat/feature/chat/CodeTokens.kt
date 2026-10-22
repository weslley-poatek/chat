package br.com.weslleycampos.chat.feature.chat

import androidx.compose.runtime.Immutable

/** What a run of code is, and so which `ChatColors.syntax` colour it takes: the prototype's `--tk-*`. */
enum class TokenKind {
    /** `cm`: `//` or `#` to the end of the line. */
    Comment,

    /** `str`: `"…"`, `'…'` or `` `…` ``; an unterminated one runs to the end of its line. */
    String,

    /** `num`: `42`, `3.14`. */
    Number,

    /** `ty`: an identifier that starts with a capital, `None` and `True` included. */
    Type,

    /** `kw`: one of the prototype's `KW`. */
    Keyword,

    /** `fn`: any other identifier directly followed by `(`. */
    Function,
}

/** A run of code of one [kind], or of plain text when [kind] is `null`. */
@Immutable
data class CodeToken(val text: String, val kind: TokenKind?)

/**
 * The prototype's `tokenize` (`ctx Prototype.dc.html` 612–624): one pass of the same alternatives,
 * in its order, so a comment wins over a string inside it and a capitalised keyword reads as a type.
 */
private val TokenPattern = Regex(
    "(//[^\\n]*|#[^\\n]*)" +
        "|(\"[^\"\\n]*\"?|'[^'\\n]*'?|`[^`]*`?)" +
        "|(\\b\\d+(?:\\.\\d+)?\\b)" +
        "|(\\b[A-Z][A-Za-z0-9_]*\\b)" +
        "|(\\b[A-Za-z_][A-Za-z0-9_]*\\b)" +
        "|([\\s\\S])",
)

/** The prototype's `KW`. */
private val Keywords = setOf(
    "import", "from", "export", "function", "const", "let", "var", "return", "if", "else", "for",
    "while", "def", "class", "async", "await", "new", "type", "interface", "extends", "typeof", "in",
    "of", "not", "and", "or", "None", "True", "False", "find", "xargs", "rm",
)

private const val CommentGroup = 1
private const val StringGroup = 2
private const val NumberGroup = 3
private const val TypeGroup = 4
private const val WordGroup = 5

/**
 * Splits [code] into the runs the prototype colours, merging neighbours of the same kind. The
 * texts joined give [code] back. It knows no language: a streaming block is tokenised again on
 * every tick, so a string still being typed reads as a string to the end of its line.
 */
fun tokenize(code: String): List<CodeToken> {
    val tokens = mutableListOf<CodeToken>()
    val run = StringBuilder()
    var runKind: TokenKind? = null

    fun push(text: String, kind: TokenKind?) {
        if (run.isNotEmpty() && kind != runKind) {
            tokens += CodeToken(run.toString(), runKind)
            run.clear()
        }
        runKind = kind
        run.append(text)
    }

    for (match in TokenPattern.findAll(code)) {
        val groups = match.groups
        when {
            groups[CommentGroup] != null -> push(match.value, TokenKind.Comment)
            groups[StringGroup] != null -> push(match.value, TokenKind.String)
            groups[NumberGroup] != null -> push(match.value, TokenKind.Number)
            groups[TypeGroup] != null -> push(match.value, TokenKind.Type)
            groups[WordGroup] != null -> push(
                match.value,
                wordKind(match.value, next = code.getOrNull(match.range.last + 1)),
            )
            else -> push(match.value, null)
        }
    }
    if (run.isNotEmpty()) tokens += CodeToken(run.toString(), runKind)
    return tokens
}

private fun wordKind(word: String, next: Char?): TokenKind? = when {
    word in Keywords -> TokenKind.Keyword
    next == '(' -> TokenKind.Function
    else -> null
}
