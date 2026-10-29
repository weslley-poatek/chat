package br.com.weslleycampos.chat.feature.voice.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single

/**
 * [SpeechInput] on Android's `SpeechRecognizer`, in the device's language, with partial results. It
 * decides on its own when the user stopped speaking. A recognizer is made for each [listen] on the
 * main thread, as Android requires, and destroyed when the collection ends.
 */
@Single
internal class AndroidSpeechInput(private val context: Context) : SpeechInput {
    override fun listen(): Flow<Heard> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) throw SpeechException(SpeechProblem.UNAVAILABLE)
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var latest = ""
        fun finish(text: String) {
            trySend(Heard.Final(text))
            close()
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle) {
                latest = partialResults.bestMatch() ?: return
                trySend(Heard.Partial(latest))
            }

            override fun onResults(results: Bundle) = finish(results.bestMatch() ?: latest)

            override fun onError(error: Int) {
                when (error) {
                    // Silence, speech it could not make out, or a recognizer still busy with the last
                    // listen: what was heard so far, or nothing, and the player listens again.
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_CLIENT,
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    -> finish(latest)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        close(SpeechException(SpeechProblem.NOT_ALLOWED))
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                    -> close(SpeechException(SpeechProblem.UNAVAILABLE))
                    else -> close(SpeechException(SpeechProblem.FAILED))
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
        )
        awaitClose { recognizer.destroy() }
    }.flowOn(Dispatchers.Main.immediate)
}

/** The recognizer's best guess at what was said, or `null` for none. */
private fun Bundle.bestMatch(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf(String::isNotBlank)
