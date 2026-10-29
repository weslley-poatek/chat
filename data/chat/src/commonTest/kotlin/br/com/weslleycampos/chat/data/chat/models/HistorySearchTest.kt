package br.com.weslleycampos.chat.data.chat.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistorySearchTest {
    private val catalogue = ModelCatalogue.models

    @Test
    fun theQueryIsTrimmedAndABlankOneListsEverything() {
        assertEquals("debounce hook", HistorySearch.text("  debounce hook \n"))
        assertNull(HistorySearch.text(""))
        assertNull(HistorySearch.text(" \t\n "))
    }

    @Test
    fun aModelMatchesWhenItsShortNameContainsTheTextWhateverItsCaseWithTheIdItHadBeforeFuelix() {
        assertEquals(listOf("gpt-5", "gpt5"), HistorySearch.modelIds("GPT", catalogue))
        assertEquals(listOf("gpt-5", "gpt5"), HistorySearch.modelIds("gpt-5", catalogue))
        assertEquals(
            listOf(
                "claude-sonnet-4-5-20250929",
                "claude-opus-4-1-20250805",
                "llama-4-maverick-17b-128e",
                "deepseek-v4.1-flash",
                "sonnet",
                "opus",
                "llama",
                "deepseek",
            ),
            HistorySearch.modelIds("4", catalogue),
        )
        assertEquals(listOf("gemini-2.5-pro", "gemini"), HistorySearch.modelIds("Pro", catalogue))
        assertEquals(listOf("o3"), HistorySearch.modelIds("o3", catalogue))
    }

    @Test
    fun theKeysModelsAreSearchedBeforeTheCatalogueAndEachIdOnce() {
        val listed = listOf(ModelCatalogue.of("claude-haiku-4-5-20251001"), ModelCatalogue.of("gpt-5"))

        assertEquals(listOf("claude-haiku-4-5-20251001"), HistorySearch.modelIds("haiku", listed))
        assertEquals(
            listOf("claude-haiku-4-5-20251001", "claude-sonnet-4-5-20250929", "sonnet"),
            HistorySearch.modelIds("4.5", listed),
        )
        assertEquals(listOf("gpt-5", "gpt5"), HistorySearch.modelIds("gpt", listed))
        assertEquals(listOf("gpt-5", "gpt5"), HistorySearch.modelIds("gpt", emptyList()))
    }

    @Test
    fun theFullNameAndTheProviderAreNotTheShortName() {
        val listed = listOf(ModelCatalogue.of("claude-haiku-4-5-20251001"))

        assertEquals(emptyList(), HistorySearch.modelIds("claude", listed + catalogue))
        assertEquals(emptyList(), HistorySearch.modelIds("OpenAI", catalogue))
        assertEquals(emptyList(), HistorySearch.modelIds("useDebounce", catalogue))
    }
}
