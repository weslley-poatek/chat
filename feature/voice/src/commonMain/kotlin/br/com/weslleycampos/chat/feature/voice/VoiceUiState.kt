package br.com.weslleycampos.chat.feature.voice

import androidx.compose.runtime.Immutable
import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerUiState
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerVariant
import br.com.weslleycampos.chat.feature.chat.picker.modelPickerUiState
import br.com.weslleycampos.chat.feature.voice.resources.FeatureVoiceRes
import br.com.weslleycampos.chat.feature.voice.resources.voice_mute
import br.com.weslleycampos.chat.feature.voice.resources.voice_phase_listening
import br.com.weslleycampos.chat.feature.voice.resources.voice_phase_speaking
import br.com.weslleycampos.chat.feature.voice.resources.voice_phase_thinking
import br.com.weslleycampos.chat.feature.voice.resources.voice_unmute
import org.jetbrains.compose.resources.StringResource

/**
 * Everything the Voice mode frame shows, the Model picker sheet over it included.
 *
 * @property conversationId The conversation the session saves into, or `null` for a new session
 *   until its first transcript creates one.
 * @property choice What answers the next exchange: the session's model and persona, or before a new
 *   session's first transcript the one picked for it. The chip, the sheet's "●" and, through
 *   [model]'s palette, the accent of the sphere, its glow and the phase label. Never comparing.
 * @property phase The prototype's `voicePhase`.
 * @property words The prototype's `voiceWords`: how many words of the line show, one more every
 *   [WordInterval], from 0 at the start of each phase.
 * @property exchange The exchange this loop plays: the session's next when listening starts, then
 *   the saved turn's; `null` until the session is first read.
 * @property muted The prototype's `voiceMuted`: only the listening sphere is stilled.
 * @property favorites The sheet's "favorites", in catalogue order.
 * @property personas The sheet's "personas", in their order.
 * @property modelPickerOpen The Model picker shows over Voice.
 */
@Immutable
data class VoiceUiState(
    val conversationId: Long? = null,
    val choice: ModelChoice = ModelChoice(ModelCatalogue.default),
    val phase: ChatVoicePhase = ChatVoicePhase.Listening,
    val words: Int = 0,
    val exchange: VoiceExchange? = null,
    val muted: Boolean = false,
    val favorites: List<ChatModel> = emptyList(),
    val personas: List<Persona> = emptyList(),
    val modelPickerOpen: Boolean = false,
) {
    /** The model that answers, a persona's own while one answers. */
    val model: ChatModel get() = choice.model

    /** The chip, the prototype's `modelShort`: "sonnet-4.5". */
    val chipLabel: String get() = model.shortName

    /** The line under the phase label. */
    val caption: VoiceCaption get() = voiceCaption(phase = phase, exchange = exchange, words = words)

    /** "listening", "thinking" or "speaking", shown in capitals. */
    val phaseLabel: StringResource get() = phase.label

    /** The label under the mute button: "mute", or "unmute" while muted. */
    val muteLabel: StringResource
        get() = if (muted) FeatureVoiceRes.string.voice_unmute else FeatureVoiceRes.string.voice_mute

    /**
     * The sheet the chip opens: the chat sheet ("Model", personas included) on what answers. Voice
     * never compares, so the caller passes no compare button and there is no hint or "◐".
     */
    val modelPicker: ModelPickerUiState
        get() = modelPickerUiState(
            variant = ModelPickerVariant.Chat,
            favorites = favorites,
            personas = personas,
            active = choice,
        )
}

/** The phase label's text, the prototype's `voicePhaseLabel`. */
val ChatVoicePhase.label: StringResource
    get() = when (this) {
        ChatVoicePhase.Listening -> FeatureVoiceRes.string.voice_phase_listening
        ChatVoicePhase.Thinking -> FeatureVoiceRes.string.voice_phase_thinking
        ChatVoicePhase.Speaking -> FeatureVoiceRes.string.voice_phase_speaking
    }
