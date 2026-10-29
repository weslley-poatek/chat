package br.com.weslleycampos.chat.data.chat.models

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelNamesTest {
    private fun names(id: String) = ModelNames.name(id) to ModelNames.shortName(id)

    @Test
    fun aClaudeIdDropsItsDateAndJoinsItsVersionAndItsShortNameDropsClaude() {
        assertEquals("Claude Haiku 4.5" to "haiku-4.5", names("claude-haiku-4-5-20251001"))
        assertEquals("Claude 3.7 Sonnet" to "3.7-sonnet", names("claude-3-7-sonnet-20250219"))
        assertEquals("Claude Opus 4" to "opus-4", names("claude-opus-4"))
    }

    @Test
    fun aDashedDateIsDroppedToo() {
        assertEquals("GPT-4o" to "gpt-4o", names("gpt-4o-2024-08-06"))
    }

    @Test
    fun aGptIdKeepsItsVersionOnGpt() {
        assertEquals("GPT-5.4 Mini" to "gpt-5.4-mini", names("gpt-5.4-mini"))
        assertEquals("GPT-4o" to "gpt-4o", names("gpt-4o"))
        assertEquals("GPT-5" to "gpt-5", names("GPT-5"))
    }

    @Test
    fun openAisReasoningModelsKeepTheirLowercaseIds() {
        assertEquals("o4-mini" to "o4-mini", names("o4-mini"))
        assertEquals("o3" to "o3", names("o3"))
    }

    @Test
    fun anyOtherIdReadsAsItsWordsCapitalisedWithItsVendorsOwnSpelling() {
        assertEquals("Gemini 3.5 Flash Lite" to "gemini-3.5-flash-lite", names("gemini-3.5-flash-lite"))
        assertEquals("DeepSeek R1" to "deepseek-r1", names("deepseek-r1"))
        assertEquals("Llama 3.3 70b Instruct" to "llama-3.3-70b-instruct", names("llama-3-3-70b-instruct"))
        assertEquals("Qwen3 235b" to "qwen3-235b", names("Qwen3-235B"))
    }

    @Test
    fun anIdWithNoWordsReadsAsItself() {
        assertEquals("" to "", names(""))
        assertEquals("-" to "-", names("-"))
        assertEquals("Claude" to "claude", names("claude"))
    }

    @Test
    fun aModelsVendorIsReadFromTheStartOfItsIdWhateverItsCase() {
        assertEquals(
            listOf(
                ModelVendor.ANTHROPIC,
                ModelVendor.OPENAI,
                ModelVendor.OPENAI,
                ModelVendor.OPENAI,
                ModelVendor.GOOGLE,
                ModelVendor.GOOGLE,
                ModelVendor.META,
                ModelVendor.DEEPSEEK,
                ModelVendor.MISTRAL,
                ModelVendor.MISTRAL,
                ModelVendor.OTHER,
            ),
            listOf(
                "Claude-Haiku-4-5",
                "gpt-5",
                "o1-preview",
                "text-embedding-3-large",
                "gemini-2.5-pro",
                "gemma-3-27b",
                "llama-4-scout",
                "deepseek-r1",
                "codestral-latest",
                "magistral-medium",
                "qwen3-235b",
            ).map(ModelVendor::of),
        )
        assertEquals("Other" to 35, ModelVendor.OTHER.displayName to ModelVendor.OTHER.hue)
    }
}
