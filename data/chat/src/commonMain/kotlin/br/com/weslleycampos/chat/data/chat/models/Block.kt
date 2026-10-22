package br.com.weslleycampos.chat.data.chat.models

/** One part of a reply, as the prototype's `{type: 'p' | 'code' | 'list'}`. */
sealed interface Block {
    /** `p`: a paragraph. */
    data class Paragraph(val text: String) : Block

    /** `code`: a code block, with [language] in its header, such as `ts`, `bash` or `python`. */
    data class Code(val language: String, val text: String) : Block

    /** `list`: a numbered list, shown as "1.", "2.", … */
    data class NumberedList(val items: List<String>) : Block
}
