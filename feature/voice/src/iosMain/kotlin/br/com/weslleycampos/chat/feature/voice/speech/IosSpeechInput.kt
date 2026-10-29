package br.com.weslleycampos.chat.feature.voice.speech

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.setActive
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognizer

/**
 * [SpeechInput] on iOS's `SFSpeechRecognizer`, in the device's language, fed by the microphone
 * through an `AVAudioEngine` tap, with partial results. iOS does not decide on its own when the user
 * stopped speaking: the player ends listening after a silence, and an error (no speech, the one
 * minute a request may last) ends it with what was heard.
 */
@OptIn(ExperimentalForeignApi::class)
@Single
internal class IosSpeechInput : SpeechInput {
    override fun listen(): Flow<Heard> = callbackFlow {
        if (!speechAllowed()) throw SpeechException(SpeechProblem.NOT_ALLOWED)
        val recognizer = SFSpeechRecognizer()
        if (!recognizer.available) throw SpeechException(SpeechProblem.UNAVAILABLE)
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeDefault,
            options = AVAudioSessionCategoryOptionDefaultToSpeaker,
            error = null,
        )
        session.setActive(true, error = null)
        val request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        val audio = AVAudioEngine()
        val microphone = audio.inputNode
        microphone.installTapOnBus(
            0u,
            bufferSize = TAP_BUFFER_FRAMES,
            format = microphone.outputFormatForBus(0u),
        ) { buffer, _ ->
            buffer?.let(request::appendAudioPCMBuffer)
        }
        audio.prepare()
        if (!audio.startAndReturnError(null)) {
            microphone.removeTapOnBus(0u)
            throw SpeechException(SpeechProblem.FAILED)
        }
        var latest = ""
        val task = recognizer.recognitionTaskWithRequest(request) { result, error ->
            if (result != null) {
                latest = result.bestTranscription.formattedString
                if (result.final) {
                    trySend(Heard.Final(latest))
                    close()
                } else {
                    trySend(Heard.Partial(latest))
                }
            } else if (error != null) {
                // No speech, speech it could not make out, or the request's time is up.
                trySend(Heard.Final(latest))
                close()
            }
        }
        awaitClose {
            task.cancel()
            audio.stop()
            microphone.removeTapOnBus(0u)
            request.endAudio()
        }
    }.flowOn(Dispatchers.Main)

    private companion object {
        /** About 20 ms of audio a tap at 48 kHz. */
        const val TAP_BUFFER_FRAMES = 1024u
    }
}
