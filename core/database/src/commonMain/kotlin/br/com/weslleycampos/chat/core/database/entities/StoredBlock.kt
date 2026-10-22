package br.com.weslleycampos.chat.core.database.entities

/**
 * One block of a reply as the `messages.blocks` column keeps it. The column is a JSON array in the
 * design prototype's own shape: `{"type":"p","text":…}`, `{"type":"code","lang":…,"text":…}` and
 * `{"type":"list","items":[…]}`.
 */
sealed interface StoredBlock {
    /** `p`: a paragraph. */
    data class Paragraph(val text: String) : StoredBlock

    /** `code`: a code block in [language], such as `ts`, `bash` or `python`. */
    data class Code(val language: String, val text: String) : StoredBlock

    /** `list`: a numbered list. */
    data class NumberedList(val items: List<String>) : StoredBlock
}
