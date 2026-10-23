package br.com.weslleycampos.chat.data.chat.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

class ModelChoiceTest {
    private val terse = PersonaPresets.personas.single { it.id == "terse" }
    private val sonnet = ModelCatalogue["sonnet"]
    private val gpt5 = ModelCatalogue["gpt5"]
    private val gemini = ModelCatalogue["gemini"]

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

    @Test
    fun aModelOrPersonaRowIsNotComparing() {
        assertFalse(ModelChoice(sonnet).compareOn)
        assertNull(ModelChoice(sonnet).comparing)
        assertFalse(ModelChoice(terse).compareOn)
        assertNull(ModelChoice(terse).compareWith)
    }

    @Test
    fun aChoiceComparesWithItsSecondModelOnlyWhileCompareIsOnNoPersonaAnswersAndItIsAnotherModel() {
        assertEquals(gpt5, ModelChoice(model = sonnet, compareOn = true, compareWith = gpt5).comparing)
        assertNull(ModelChoice(model = sonnet, compareOn = true).comparing)
        assertNull(ModelChoice(model = sonnet, compareOn = false, compareWith = gpt5).comparing)
        assertNull(ModelChoice(model = gpt5, persona = terse, compareOn = true, compareWith = sonnet).comparing)
        assertNull(ModelChoice(model = sonnet, compareOn = true, compareWith = sonnet).comparing)
    }

    @Test
    fun theCompareButtonTurnsCompareOnWithTheModelAndOffDroppingTheSecondModelAndThePersonaGoesEitherWay() {
        val on = ModelChoice(sonnet).toggledCompare()

        assertEquals(ModelChoice(model = sonnet, compareOn = true), on)
        assertEquals(ModelChoice(sonnet), on.toggledCompare())
        val comparing = ModelChoice(model = sonnet, compareOn = true, compareWith = gpt5)
        assertEquals(ModelChoice(sonnet), comparing.toggledCompare())
        assertEquals(ModelChoice(model = gpt5, compareOn = true), ModelChoice(terse).toggledCompare())
    }

    @Test
    fun whileComparingAModelRowBecomesTheSecondModelAndTheFirstModelsOwnRowChangesNothing() {
        val waiting = ModelChoice(model = sonnet, compareOn = true)
        val comparing = ModelChoice(model = sonnet, compareOn = true, compareWith = gpt5)

        assertEquals(comparing, waiting.picked(ModelChoice(gpt5)))
        assertEquals(comparing.copy(compareWith = gemini), comparing.picked(ModelChoice(gemini)))
        assertNull(waiting.picked(ModelChoice(sonnet)))
        assertNull(comparing.picked(ModelChoice(sonnet)))
    }

    @Test
    fun aPersonaRowTurnsCompareOffAndWithCompareOffAModelRowPicksThatModelOnItsOwn() {
        val comparing = ModelChoice(model = sonnet, compareOn = true, compareWith = gpt5)

        assertEquals(ModelChoice(terse), comparing.picked(ModelChoice(terse)))
        assertEquals(ModelChoice(gemini), ModelChoice(sonnet).picked(ModelChoice(gemini)))
        assertEquals(ModelChoice(sonnet), ModelChoice(sonnet).picked(ModelChoice(sonnet)))
        assertEquals(ModelChoice(gemini), ModelChoice(terse).picked(ModelChoice(gemini)))
    }

    @Test
    fun aConversationsChoiceCarriesItsCompareAndItComparesAsItsChoiceDoes() {
        val conversation = Conversation(
            id = 1,
            title = "SSE vs WebSocket",
            model = sonnet,
            createdAt = Instant.fromEpochMilliseconds(0),
            updatedAt = Instant.fromEpochMilliseconds(0),
            compareOn = true,
            compareWith = gpt5,
        )

        assertEquals(ModelChoice(model = sonnet, compareOn = true, compareWith = gpt5), conversation.choice)
        assertEquals(gpt5, conversation.comparing)
        assertNull(conversation.copy(compareOn = false).comparing)
    }
}
