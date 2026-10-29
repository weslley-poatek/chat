package br.com.weslleycampos.chat.feature.voice.speech

import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.feature.voice.SilenceAfterSpeech
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.setActive
import platform.Foundation.NSLocale
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFTranscription
import platform.Speech.SFTranscriptionSegment
import kotlin.time.Duration.Companion.milliseconds

/**
 * [SpeechInput] on iOS's `SFSpeechRecognizer`, fed by the microphone through an `AVAudioEngine` tap,
 * with partial results. A recognizer hears one language, so there is one per language listened for,
 * all on the same microphone.
 *
 * iOS does not decide on its own when the user stopped speaking: after [EndAudioAfter] with no new
 * word, the audio ends and each recognizer gives its final transcript, with how sure it is of it.
 * The surest is what was heard, and the next listen shows that language's words while they come; a
 * player whose own silence comes first takes the words shown. An error (no speech, the one minute a
 * request may last) ends a recognizer with what it heard.
 */
@OptIn(ExperimentalForeignApi::class)
@Single
internal class IosSpeechInput : SpeechInput {
    /** The language the last listen was heard in; only touched on the main thread. */
    private var lastHeard: VoiceLanguage? = null

    override fun listen(languages: List<VoiceLanguage>): Flow<Heard> = callbackFlow {
        if (!speechAllowed()) throw SpeechException(SpeechProblem.NOT_ALLOWED)
        val listeners = languages.map(::Listener).filter { it.recognizer.available }
        if (listeners.isEmpty()) throw SpeechException(SpeechProblem.UNAVAILABLE)
        val shown = listeners.firstOrNull { it.language == lastHeard } ?: listeners.first()
        val microphone = Microphone(listeners)
        var silence: Job? = null
        val tasks = listeners.map { listener ->
            listener.recognizer.recognitionTaskWithRequest(listener.request) { result, error ->
                if (result != null) {
                    listener.text = result.bestTranscription.formattedString
                    if (result.final) {
                        listener.finish(result.bestTranscription.confidence())
                    } else {
                        trySend(Heard.Partial(shown.text.ifBlank { listener.text }))
                        silence?.cancel()
                        silence = launch {
                            delay(EndAudioAfter)
                            microphone.stop()
                        }
                    }
                } else if (error != null) {
                    // No speech, speech it could not make out, or the request's time is up.
                    listener.finish(confidence = 0.0)
                }
                if (listeners.all(Listener::done)) {
                    val surest = listeners.filter { it.text.isNotBlank() }.maxByOrNull(Listener::confidence) ?: shown
                    lastHeard = surest.language
                    trySend(Heard.Final(surest.text))
                    close()
                }
            }
        }
        awaitClose {
            silence?.cancel()
            tasks.forEach { it.cancel() }
            microphone.stop()
        }
    }.flowOn(Dispatchers.Main)

    private companion object {
        /** Short of the player's own [SilenceAfterSpeech], so the final transcripts come first. */
        val EndAudioAfter = SilenceAfterSpeech - 500.milliseconds
    }
}

/** One language's recognizer and request, and what it heard. */
@OptIn(ExperimentalForeignApi::class)
private class Listener(val language: VoiceLanguage) {
    val recognizer = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = language.tag))
    val request = SFSpeechAudioBufferRecognitionRequest().apply { shouldReportPartialResults = true }

    /** The words heard so far, or all of them once [done]. */
    var text = ""

    /** How sure the recognizer is of [text], from 0 to 1, once [done]. */
    var confidence = 0.0
        private set

    /** The recognizer gave its final transcript, or failed. */
    var done = false
        private set

    fun finish(confidence: Double) {
        this.confidence = confidence
        done = true
    }
}

/** The microphone, feeding every listener's request from when it is made until [stop]. */
@OptIn(ExperimentalForeignApi::class)
private class Microphone(private val listeners: List<Listener>) {
    private val audio = AVAudioEngine()
    private val input = audio.inputNode
    private var on = true

    init {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeDefault,
            options = AVAudioSessionCategoryOptionDefaultToSpeaker,
            error = null,
        )
        session.setActive(true, error = null)
        input.installTapOnBus(0u, bufferSize = TAP_BUFFER_FRAMES, format = input.outputFormatForBus(0u)) { buffer, _ ->
            buffer?.let { heard -> listeners.forEach { it.request.appendAudioPCMBuffer(heard) } }
        }
        audio.prepare()
        if (!audio.startAndReturnError(null)) {
            input.removeTapOnBus(0u)
            throw SpeechException(SpeechProblem.FAILED)
        }
    }

    /** Turns the microphone off and tells every request its audio ended; once. */
    fun stop() {
        if (!on) return
        on = false
        audio.stop()
        input.removeTapOnBus(0u)
        listeners.forEach { it.request.endAudio() }
    }

    private companion object {
        /** About 20 ms of audio a tap at 48 kHz. */
        const val TAP_BUFFER_FRAMES = 1024u
    }
}

/** How sure the recognizer is of this transcript: its words' confidence on average, 0 for none. */
private fun SFTranscription.confidence(): Double {
    val words = segments.filterIsInstance<SFTranscriptionSegment>()
    return if (words.isEmpty()) 0.0 else words.sumOf { it.confidence.toDouble() } / words.size
}
