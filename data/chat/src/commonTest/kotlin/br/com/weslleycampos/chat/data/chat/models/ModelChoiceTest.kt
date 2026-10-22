package br.com.weslleycampos.chat.data.chat.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ModelChoiceTest {
    private val terse = PersonaPresets.personas.single { it.id == "terse" }

    @Test
    fun aModelRowPicksTheModelOnItsOwn() {
        val choice = ModelChoice(ModelCatalogue["o3"])

        assertEquals(ModelCatalogue["o3"], choice.model)
        assertNull(choice.persona)
    }

    @Test
    fun aPersonaRowPicksThePersonaAndItsModel() {
        assertEquals(ModelChoice(model = ModelCatalogue["gpt5"], persona = terse), ModelChoice(terse))
    }

    @Test
    fun aConversationsChoiceIsItsModelAndPersona() {
        val conversation = Conversation(
            id = 1,
            title = "Postgres index",
            model = ModelCatalogue["gpt5"],
            persona = terse,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
        )

        assertEquals(ModelChoice(terse), conversation.choice)
        assertEquals(ModelChoice(ModelCatalogue["gpt5"]), conversation.copy(persona = null).choice)
    }
}
