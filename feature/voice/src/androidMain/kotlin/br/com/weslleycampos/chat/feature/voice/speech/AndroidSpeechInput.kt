package br.com.weslleycampos.chat.feature.voice.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single

/**
 * [SpeechInput] on Android's `SpeechRecognizer`, with partial results. It decides on its own when
 * the user stopped speaking. A recognizer is made for each [listen] on the main thread, as Android
 * requires, and destroyed when the collection ends.
 *
 * For more than one language, from Android 14 the recognizer detects which one is spoken and
 * switches to it (`EXTRA_ENABLE_LANGUAGE_SWITCH`), and each listen starts in the language it last
 * detected, since people tend to stay in one for a while. A recognizer that cannot switch, or an
 * older Android, listens in the first language alone.
 */
@Single
internal class AndroidSpeechInput(private val context: Context) : SpeechInput {
    /** The language the recognizer last detected speech in; only touched on the main thread. */
    private var lastHeard: VoiceLanguage? = null

    override fun listen(languages: List<VoiceLanguage>): Flow<Heard> = callbackFlow {
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

            override fun onLanguageDetection(results: Bundle) {
                val detected = results.getString(SpeechRecognizer.DETECTED_LANGUAGE)?.let(VoiceLanguage::heardAs)
                if (detected in languages) lastHeard = detected
            }

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
        val first = lastHeard?.takeIf { it in languages } ?: languages.first()
        recognizer.startListening(recognition(first, languages))
        awaitClose { recognizer.destroy() }
    }.flowOn(Dispatchers.Main.immediate)

    /** What to listen for: [first], and from Android 14 a switch to any other of [languages] it detects. */
    private fun recognition(first: VoiceLanguage, languages: List<VoiceLanguage>): Intent {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, first.tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (languages.size > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val tags = ArrayList(languages.map(VoiceLanguage::tag))
            intent
                .putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                .putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, tags)
                // Balanced: switches once fairly sure, so an English term in a Portuguese sentence
                // does not switch the whole sentence to English.
                .putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                .putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, tags)
        }
        return intent
    }
}

/** The recognizer's best guess at what was said, or `null` for none. */
private fun Bundle.bestMatch(): String? =
    getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf(String::isNotBlank)
