package br.com.weslleycampos.chat.core.database.daos

/** The escape character the DAOs' `LIKE … ESCAPE '\'` clauses name. */
private const val LIKE_ESCAPE = '\\'

/**
 * A `LIKE` pattern, for a query whose escape character is `\`, that matches every value containing
 * [text]: `%`, `_` and `\` in [text] match only themselves, as [HistoryDao.search] expects.
 */
fun containsPattern(text: String): String = buildString(text.length + 2) {
    append('%')
    text.forEach { char ->
        if (char == '%' || char == '_' || char == LIKE_ESCAPE) append(LIKE_ESCAPE)
        append(char)
    }
    append('%')
}
