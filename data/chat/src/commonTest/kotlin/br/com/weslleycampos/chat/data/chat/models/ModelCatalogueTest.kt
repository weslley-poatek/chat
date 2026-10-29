package br.com.weslleycampos.chat.data.chat.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModelCatalogueTest {
    @Test
    fun theCatalogueIsThePrototypesEightModelsOnTheirFuelixIdsInItsOrder() {
        val rows = ModelCatalogue.models.map {
            listOf(it.id, it.name, it.shortName, it.provider, it.contextWindow, it.cost, it.hue).joinToString(" · ")
        }

        assertEquals(
            listOf(
                "claude-sonnet-4-5-20250929 · Claude Sonnet 4.5 · sonnet-4.5 · Anthropic · 200k · $$ · 35",
                "claude-opus-4-1-20250805 · Claude Opus 4.1 · opus-4.1 · Anthropic · 200k · $$$ · 35",
                "gpt-5 · GPT-5 · gpt-5 · OpenAI · 400k · $$ · 150",
                "o3 · o3 · o3 · OpenAI · 200k · $$$ · 150",
                "gemini-2.5-pro · Gemini 2.5 Pro · gemini-2.5-pro · Google · 1M · $$ · 255",
                "llama-4-maverick-17b-128e · Llama 4 Maverick · llama-4 · Meta · 1M · $ · 290",
                "deepseek-v4.1-flash · DeepSeek V4.1 Flash · deepseek-v4.1-flash · DeepSeek · null · $ · 220",
                "mistral-large · Mistral Large · mistral-large · Mistral · 128k · $ · 80",
            ),
            rows,
        )
    }

    @Test
    fun theDefaultModelIsClaudeSonnet() {
        assertEquals("claude-sonnet-4-5-20250929", ModelCatalogue.DEFAULT_MODEL_ID)
        assertEquals("Claude Sonnet 4.5", ModelCatalogue.default.name)
        assertSame(ModelCatalogue.models.first(), ModelCatalogue.default)
    }

    @Test
    fun everyIdStoredBeforeFuelixReadsAsTheModelItBecame() {
        assertEquals(
            ModelCatalogue.models.map { it.id },
            listOf("sonnet", "opus", "gpt5", "o3", "gemini", "llama", "deepseek", "mistral")
                .map(ModelCatalogue::resolve),
        )
        assertSame(ModelCatalogue.default, ModelCatalogue["sonnet"])
        assertSame(ModelCatalogue.find("gpt-5"), ModelCatalogue.find("gpt5"))
        assertEquals("gpt-5", ModelCatalogue.resolve("gpt-5"))
        assertEquals("gpt-6", ModelCatalogue.resolve("gpt-6"))
    }

    @Test
    fun anyOtherIdIsAModelNamedFromItAndOnlyABlankOneFindsNothingAndReadsAsTheDefault() {
        assertEquals(
            ChatModel(
                id = "claude-haiku-4-5-20251001",
                name = "Claude Haiku 4.5",
                shortName = "haiku-4.5",
                vendor = ModelVendor.ANTHROPIC,
            ),
            ModelCatalogue.find("claude-haiku-4-5-20251001"),
        )
        assertEquals(ModelCatalogue.of("gpt-6"), ModelCatalogue["gpt-6"])
        assertEquals("GPT-5", ModelCatalogue.find(" gpt-5 ")?.name)
        assertNull(ModelCatalogue.find(""))
        assertNull(ModelCatalogue.find(" \t"))
        assertSame(ModelCatalogue.default, ModelCatalogue[""])
    }

    @Test
    fun aCatalogueIdReadsWithItsDesignValuesAndAnyOtherWithNone() {
        assertSame(ModelCatalogue.models[2], ModelCatalogue.of("gpt-5"))
        val other = ModelCatalogue.of("qwen3-235b")
        assertEquals(ModelVendor.OTHER, other.vendor)
        assertNull(other.contextWindow)
        assertNull(other.cost)
    }

    @Test
    fun chatModelsAreListedAndModelsThatEmbedDrawTranscribeSpeakOrModerateAreNot() {
        listOf(
            "claude-sonnet-4-5-20250929",
            "gpt-5",
            "o3",
            "gemini-2.5-pro",
            "llama-4-maverick-17b-128e",
            "qwen3-235b",
        ).forEach { assertTrue(ModelCatalogue.isChatModel(it), it) }
        listOf(
            "text-embedding-3-large",
            "Text-Embedding-3-Small",
            "whisper-1",
            "tts-1",
            "gpt-4o-mini-tts",
            "dall-e-3",
            "gpt-image-1",
            "llama-guard-3",
            "omni-moderation-latest",
            "voxtral-mini",
            "gpt-4o-transcribe",
            "gpt-4o-realtime-preview",
            "gpt-4o-audio-preview",
            "rerank-v3",
            "claude-sonnet-4-5-anthropic",
        ).forEach { assertFalse(ModelCatalogue.isChatModel(it), it) }
    }
}
