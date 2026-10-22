package br.com.weslleycampos.chat.data.chat.models

/**
 * How History's search box matches, the prototype's `hQuery` filter with message text added: the
 * query is trimmed, a blank one lists everything, and a conversation matches when its title, one of
 * the user's own messages, or its model's short name contains the query, whatever its case.
 */
internal object HistorySearch {
    /** The text to look for, or `null` for a blank [query], which lists every conversation. */
    fun text(query: String): String? = query.trim().ifEmpty { null }

    /**
     * The ids of the catalogue models whose short name, as History's rows show it ("gpt-5"),
     * contains [text], ignoring case: "GPT" finds `gpt5`, "4" finds sonnet-4.5, opus-4.1 and
     * llama-4.
     */
    fun modelIds(text: String): List<String> =
        ModelCatalogue.models.filter { it.shortName.contains(text, ignoreCase = true) }.map(ChatModel::id)
}
