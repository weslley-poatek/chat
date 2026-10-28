package br.com.weslleycampos.chat.data.settings.models

import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelProviderTest {
    @Test
    fun theProvidersAreThePrototypesInItsOrder() {
        assertEquals(
            listOf("Anthropic", "OpenAI", "Google", "Meta", "DeepSeek", "Mistral"),
            ModelProvider.entries.map(ModelProvider::displayName),
        )
        assertEquals(
            listOf("anthropic", "openai", "google", "meta", "deepseek", "mistral"),
            ModelProvider.entries.map(ModelProvider::id),
        )
    }

    @Test
    fun anthropicAndOpenAiHintTheirKeyPrefixAndTheOthersSayPasteKey() {
        assertEquals(
            listOf("sk-ant-…", "sk-…", "paste key", "paste key", "paste key", "paste key"),
            ModelProvider.entries.map(ModelProvider::placeholder),
        )
    }

    @Test
    fun everyModelInTheCatalogueHasItsProvidersKeyRow() {
        assertEquals(
            ModelProvider.entries.map(ModelProvider::displayName),
            ModelCatalogue.models.map { it.provider }.distinct(),
        )
    }
}
