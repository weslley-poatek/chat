package br.com.weslleycampos.chat.data.chat.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ModelCatalogueTest {
    @Test
    fun theCatalogueIsThePrototypesEightModelsInItsOrder() {
        val rows = ModelCatalogue.models.map {
            listOf(it.id, it.name, it.shortName, it.provider, it.contextWindow, it.cost, it.hue.toString())
        }

        assertEquals(
            listOf(
                listOf("sonnet", "Claude Sonnet 4.5", "sonnet-4.5", "Anthropic", "200k", "$$", "35"),
                listOf("opus", "Claude Opus 4.1", "opus-4.1", "Anthropic", "200k", "$$$", "35"),
                listOf("gpt5", "GPT-5", "gpt-5", "OpenAI", "400k", "$$", "150"),
                listOf("o3", "o3", "o3", "OpenAI", "200k", "$$$", "150"),
                listOf("gemini", "Gemini 2.5 Pro", "gemini-2.5-pro", "Google", "1M", "$$", "255"),
                listOf("llama", "Llama 4 Maverick", "llama-4", "Meta", "1M", "$", "290"),
                listOf("deepseek", "DeepSeek V3.1", "deepseek-v3.1", "DeepSeek", "128k", "$", "220"),
                listOf("mistral", "Mistral Large 3", "mistral-large", "Mistral", "128k", "$", "80"),
            ),
            rows,
        )
    }

    @Test
    fun theDefaultModelIsClaudeSonnet() {
        assertEquals("sonnet", ModelCatalogue.DEFAULT_MODEL_ID)
        assertEquals("Claude Sonnet 4.5", ModelCatalogue.default.name)
        assertSame(ModelCatalogue.models.first(), ModelCatalogue.default)
    }

    @Test
    fun anIdOutsideTheCatalogueFindsNothingAndReadsAsTheDefault() {
        assertEquals("GPT-5", ModelCatalogue.find("gpt5")?.name)
        assertEquals("GPT-5", ModelCatalogue["gpt5"].name)
        assertNull(ModelCatalogue.find("gpt-5"))
        assertSame(ModelCatalogue.default, ModelCatalogue["gpt-5"])
    }
}
