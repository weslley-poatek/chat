package br.com.weslleycampos.chat.data.chat.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonaPresetsTest {
    @Test
    fun thePresetsAreThePrototypesThreePersonasInItsOrder() {
        val rows = PersonaPresets.personas.map { listOf(it.id, it.name, it.model.id, it.prompt) }

        assertEquals(
            listOf(
                listOf(
                    "reviewer",
                    "Code reviewer",
                    "claude-sonnet-4-5-20250929",
                    "Correctness first, style last. Cite line numbers. No praise.",
                ),
                listOf("terse", "Terse", "gpt-5", "One paragraph max. Code over prose."),
                listOf("digest", "Paper digest", "gemini-2.5-pro", "Claim, method, evidence, caveats. Bullets."),
            ),
            rows,
        )
    }

    @Test
    fun everyPresetAnswersWithACatalogueModel() {
        PersonaPresets.personas.forEach { persona ->
            assertTrue(persona.model in ModelCatalogue.models, persona.id)
        }
    }
}
