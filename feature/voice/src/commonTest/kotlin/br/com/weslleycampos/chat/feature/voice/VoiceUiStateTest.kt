package br.com.weslleycampos.chat.feature.voice

import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.feature.chat.palette
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerMark
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerVariant
import br.com.weslleycampos.chat.feature.voice.resources.FeatureVoiceRes
import br.com.weslleycampos.chat.feature.voice.resources.voice_mute
import br.com.weslleycampos.chat.feature.voice.resources.voice_phase_listening
import br.com.weslleycampos.chat.feature.voice.resources.voice_phase_speaking
import br.com.weslleycampos.chat.feature.voice.resources.voice_phase_thinking
import br.com.weslleycampos.chat.feature.voice.resources.voice_unmute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class VoiceUiStateTest {
    @Test
    fun aNewSessionListensOnTheDefaultModelWithNothingToShowYet() {
        val state = VoiceUiState()

        assertNull(state.conversationId)
        assertEquals(ChatVoicePhase.Listening, state.phase)
        assertEquals(ModelChoice(ModelCatalogue.default), state.choice)
        assertEquals(VoiceCaption.Empty, state.caption)
        assertFalse(state.muted)
        assertFalse(state.modelPickerOpen)
    }

    @Test
    fun theLabelIsThePhaseAndTheMuteLabelSaysWhatATapDoes() {
        assertEquals(FeatureVoiceRes.string.voice_phase_listening, VoiceUiState().phaseLabel)
        assertEquals(
            FeatureVoiceRes.string.voice_phase_thinking,
            VoiceUiState(phase = ChatVoicePhase.Thinking).phaseLabel,
        )
        assertEquals(
            FeatureVoiceRes.string.voice_phase_speaking,
            VoiceUiState(phase = ChatVoicePhase.Speaking).phaseLabel,
        )
        assertEquals(FeatureVoiceRes.string.voice_mute, VoiceUiState().muteLabel)
        assertEquals(FeatureVoiceRes.string.voice_unmute, VoiceUiState(muted = true).muteLabel)
    }

    @Test
    fun theChipIsTheShortNameOfWhatAnswersAndTheAccentIsItsPalette() {
        assertEquals("sonnet-4.5", VoiceUiState().chipLabel)
        val gemini = VoiceUiState(choice = ModelChoice(ModelCatalogue["gemini"]))
        assertEquals("gemini-2.5-pro", gemini.chipLabel)
        assertEquals(ChatPalette.GEMINI, gemini.model.palette)
        // A persona answers with its model, whose short name the chip shows.
        val persona = PersonaPresets.personas.first()
        assertEquals(persona.model.shortName, VoiceUiState(choice = ModelChoice(persona)).chipLabel)
    }

    @Test
    fun theSheetIsTheChatSheetWithPersonasAndNoCompareMarkingWhatAnswers() {
        val persona = PersonaPresets.personas.first()
        val sheet = VoiceUiState(
            choice = ModelChoice(persona),
            favorites = listOf(ModelCatalogue.default),
            personas = PersonaPresets.personas,
        ).modelPicker

        assertEquals(ModelPickerVariant.Chat, sheet.variant)
        assertFalse(sheet.compareOn)
        assertNull(sheet.compareHint)
        assertEquals(PersonaPresets.personas.size, sheet.personas.size)
        assertEquals(
            listOf("persona:${persona.id}"),
            (sheet.favorites + sheet.personas + sheet.allModels).filter { it.mark != null }.map { it.key },
        )
        assertEquals(ModelPickerMark.Active, sheet.personas.first().mark)
    }

    @Test
    fun previewsCoverEveryStateInLightAndDark() {
        val cases = VoiceScreenPreviewParameterProvider().values.toList()
        val names = listOf(
            "Listening · starting",
            "Listening",
            "Thinking",
            "Speaking",
            "Speaking · time up",
            "Muted",
            "Persona",
            "Gemini",
            "Loading",
        )

        for (dark in listOf(false, true)) {
            assertEquals(names, cases.filter { it.darkTheme == dark }.map { it.name })
        }
        assertEquals(names.size * 2, cases.map { it.displayName }.distinct().size)
        val states = cases.map { it.uiState }
        assertEquals(ChatVoicePhase.entries.toSet(), states.map { it.phase }.toSet())
        assertEquals(setOf(false, true), states.map { it.muted }.toSet())
    }
}
