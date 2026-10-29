package br.com.weslleycampos.chat.feature.voice.speech

import br.com.weslleycampos.chat.feature.voice.resources.FeatureVoiceRes
import br.com.weslleycampos.chat.feature.voice.resources.voice_toast_failed
import br.com.weslleycampos.chat.feature.voice.resources.voice_toast_not_allowed
import br.com.weslleycampos.chat.feature.voice.resources.voice_toast_unavailable
import org.jetbrains.compose.resources.StringResource

/** The toast that says why the device could not listen. */
val SpeechProblem.toast: StringResource
    get() = when (this) {
        SpeechProblem.NOT_ALLOWED -> FeatureVoiceRes.string.voice_toast_not_allowed
        SpeechProblem.UNAVAILABLE -> FeatureVoiceRes.string.voice_toast_unavailable
        SpeechProblem.FAILED -> FeatureVoiceRes.string.voice_toast_failed
    }
