package br.com.weslleycampos.chat.data.chat.models

/** How a conversation is named after the message that starts it. */
internal object ConversationTitles {
    /** The prototype's `draft.trim().slice(0, 40)`. */
    const val MAX_LENGTH = 40

    private val whitespace = Regex("\\s+")

    /**
     * [firstMessage] on one line, cut to [MAX_LENGTH] characters: every run of whitespace, line
     * breaks included, becomes one space, and the ends are trimmed. A cut never splits an emoji or
     * another character made of two UTF-16 units.
     */
    fun from(firstMessage: String): String {
        val line = firstMessage.replace(whitespace, " ").trim()
        if (line.length <= MAX_LENGTH) return line
        val end = if (line[MAX_LENGTH - 1].isHighSurrogate()) MAX_LENGTH - 1 else MAX_LENGTH
        return line.substring(0, end).trimEnd()
    }
}
