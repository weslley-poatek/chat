package br.com.weslleycampos.chat.data.chat.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistorySearchTest {
    @Test
    fun theQueryIsTrimmedAndABlankOneListsEverything() {
        assertEquals("debounce hook", HistorySearch.text("  debounce hook \n"))
        assertNull(HistorySearch.text(""))
        assertNull(HistorySearch.text(" \t\n "))
    }

    @Test
    fun aModelMatchesWhenItsShortNameContainsTheTextWhateverItsCase() {
        assertEquals(listOf("gpt5"), HistorySearch.modelIds("GPT"))
        assertEquals(listOf("gpt5"), HistorySearch.modelIds("gpt-5"))
        assertEquals(listOf("sonnet", "opus", "llama"), HistorySearch.modelIds("4"))
        assertEquals(listOf("gemini"), HistorySearch.modelIds("Pro"))
        assertEquals(listOf("o3"), HistorySearch.modelIds("o3"))
    }

    @Test
    fun theFullNameAndTheProviderAreNotTheShortName() {
        assertEquals(emptyList(), HistorySearch.modelIds("claude"))
        assertEquals(emptyList(), HistorySearch.modelIds("OpenAI"))
        assertEquals(emptyList(), HistorySearch.modelIds("useDebounce"))
    }
}
