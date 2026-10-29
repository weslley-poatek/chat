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
     * The ids a conversation's model may be stored under whose short name, as History's rows show
     * it ("gpt-5"), contains [text], ignoring case: among [listed], the key's models, and
     * [ModelCatalogue.models], with the ids stored before Fuelix that read as one of them. "GPT"
     * finds `gpt-5` and the older `gpt5`; "4" finds sonnet-4.5, opus-4.1, llama-4 and
     * deepseek-v4.1-flash.
     */
    fun modelIds(text: String, listed: List<ChatModel>): List<String> {
        val matching = (listed + ModelCatalogue.models)
            .filter { it.shortName.contains(text, ignoreCase = true) }
            .mapTo(linkedSetOf(), ChatModel::id)
        return (matching + ModelCatalogue.legacyIds.filterValues { it in matching }.keys).toList()
    }
}
