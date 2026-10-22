package br.com.weslleycampos.chat.data.chat.models

import kotlin.test.Test
import kotlin.test.assertEquals

class PersonaPresetsTest {
    @Test
    fun thePresetsAreThePrototypesThreePersonasInItsOrder() {
        val rows = PersonaPresets.personas.map { listOf(it.id, it.name, it.model.id, it.prompt) }

        assertEquals(
            listOf(
                listOf(
                    "reviewer",
                    "Code reviewer",
                    "sonnet",
                    "Correctness first, style last. Cite line numbers. No praise.",
                ),
                listOf("terse", "Terse", "gpt5", "One paragraph max. Code over prose."),
                listOf("digest", "Paper digest", "gemini", "Claim, method, evidence, caveats. Bullets."),
            ),
            rows,
        )
    }

    @Test
    fun everyPresetAnswersWithACatalogueModel() {
        PersonaPresets.personas.forEach { persona ->
            assertEquals(ModelCatalogue.find(persona.model.id), persona.model)
        }
    }
}
