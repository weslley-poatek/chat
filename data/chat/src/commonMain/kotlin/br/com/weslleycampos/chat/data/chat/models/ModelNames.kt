package br.com.weslleycampos.chat.data.chat.models

/**
 * A model's names read from its Fuelix id, for the models [ModelCatalogue] has no design values for.
 * The id's date suffix (`-20251001`, `-2024-08-06`) is dropped and its version numbers joined with
 * dots, so `claude-haiku-4-5-20251001` reads as "Claude Haiku 4.5" and "haiku-4.5".
 */
internal object ModelNames {
    private const val DATE_LENGTH = 8
    private const val CLAUDE = "claude"
    private const val GPT = "gpt"
    private val DashedDate = Regex("""-\d{4}-\d{2}-\d{2}$""")

    /** The words of the full name: "Claude Haiku 4.5", "GPT-5.4 Mini", "Gemini 3.5 Flash Lite", "o4-mini". */
    fun name(id: String): String {
        val words = words(id)
        val first = words.firstOrNull() ?: return id
        return when {
            // OpenAI's reasoning models keep their lowercase ids: "o3", "o4-mini".
            first.length == 2 && first[0] == 'o' && first[1].isDigit() -> words.joinToString("-")
            first == GPT && words.size > 1 -> (listOf("GPT-${words[1]}") + words.drop(2).map(::capitalized))
                .joinToString(" ")
            else -> words.joinToString(" ") { brand(it) ?: capitalized(it) }
        }
    }

    /** The short name: the id without its date and without "claude-": "haiku-4.5", "gpt-5.4-mini". */
    fun shortName(id: String): String = words(id).let { words ->
        if (words.size > 1 && words.first() == CLAUDE) words.drop(1) else words
    }.joinToString("-").ifEmpty { id }

    /** The id's lowercase words, its date dropped and each run of number words joined with dots. */
    private fun words(id: String): List<String> {
        val parts = id.lowercase().replace(DashedDate, "").split('-').filter(String::isNotEmpty)
        val undated = if (parts.size > 1 && parts.last().isDate()) parts.dropLast(1) else parts
        return buildList {
            for (part in undated) {
                val previous = lastOrNull()
                if (part.isNumber() && previous != null && previous.isVersion()) {
                    set(lastIndex, "$previous.$part")
                } else {
                    add(part)
                }
            }
        }
    }

    private fun String.isDate(): Boolean = length == DATE_LENGTH && all(Char::isDigit)

    private fun String.isNumber(): Boolean = isNotEmpty() && all(Char::isDigit)

    private fun String.isVersion(): Boolean = isNotEmpty() && all { it.isDigit() || it == '.' }

    /** How a vendor writes its own name. */
    private fun brand(word: String): String? = when (word) {
        "deepseek" -> "DeepSeek"
        "gpt" -> "GPT"
        else -> null
    }

    private fun capitalized(word: String): String = word.replaceFirstChar(Char::uppercaseChar)
}
