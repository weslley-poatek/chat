package br.com.weslleycampos.chat.feature.voice.speech

import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSRange
import platform.darwin.NSObject

/**
 * [SpeechOutput] on iOS's `AVSpeechSynthesizer`, in the best voice installed for the language it is
 * given: an enhanced or premium one the user downloaded in iOS Settings before the default compact
 * one. One synthesizer for the process; each word is reported as it starts
 * (`willSpeakRangeOfSpeechString`).
 */
@OptIn(ExperimentalForeignApi::class)
@Single
internal class IosSpeechOutput : SpeechOutput {
    private val synthesizer = AVSpeechSynthesizer()

    override fun speak(text: String, language: VoiceLanguage): Flow<Int> = callbackFlow {
        val starts = wordStarts(text)
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.voice = bestVoice(language)
        // The synthesizer holds its delegate weakly: this flow keeps it until it closes.
        val progress = Progress(
            utterance = utterance,
            onWord = { location -> trySend(starts.count { it <= location }) },
            onDone = {
                trySend(starts.size)
                close()
            },
            onCancel = { close() },
        )
        synthesizer.delegate = progress
        synthesizer.speakUtterance(utterance)
        awaitClose {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
            if (synthesizer.delegate == progress) synthesizer.delegate = null
        }
    }.flowOn(Dispatchers.Main)
}

/** The highest quality voice installed for [language], or iOS's default one for it. */
private fun bestVoice(language: VoiceLanguage): AVSpeechSynthesisVoice? =
    AVSpeechSynthesisVoice.speechVoices()
        .filterIsInstance<AVSpeechSynthesisVoice>()
        .filter { it.language == language.tag }
        .maxByOrNull { it.quality }
        ?: AVSpeechSynthesisVoice.voiceWithLanguage(language.tag)

/** [utterance]'s progress: the character each word starts at, then its end or its cancel. */
@OptIn(ExperimentalForeignApi::class)
private class Progress(
    private val utterance: AVSpeechUtterance,
    private val onWord: (location: Int) -> Unit,
    private val onDone: () -> Unit,
    private val onCancel: () -> Unit,
) : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        willSpeakRangeOfSpeechString: CValue<NSRange>,
        utterance: AVSpeechUtterance,
    ) {
        if (utterance == this.utterance) onWord(willSpeakRangeOfSpeechString.useContents { location.toInt() })
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
        if (didFinishSpeechUtterance == utterance) onDone()
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didCancelSpeechUtterance: AVSpeechUtterance) {
        if (didCancelSpeechUtterance == utterance) onCancel()
    }
}
