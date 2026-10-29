package br.com.weslleycampos.chat.feature.voice.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single
import java.util.Locale
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * [SpeechOutput] on Android's `TextToSpeech`, in the engine's voice for the language it is given. One
 * engine for the process, started on first use; each word is reported as the engine starts it
 * (`onRangeStart`). A language whose voice is not installed cannot be spoken.
 */
@OptIn(ExperimentalAtomicApi::class)
@Single
internal class AndroidSpeechOutput(context: Context) : SpeechOutput {
    private val ready = CompletableDeferred<Boolean>()
    private val engine = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }
    private val utterances = AtomicInt(0)

    override fun speak(text: String, language: VoiceLanguage): Flow<Int> = callbackFlow {
        if (!ready.await()) throw SpeechException(SpeechProblem.UNAVAILABLE)
        val available = engine.setLanguage(Locale.forLanguageTag(language.tag))
        if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) {
            throw SpeechException(SpeechProblem.UNAVAILABLE)
        }
        val id = "reply-${utterances.incrementAndFetch()}"
        val starts = wordStarts(text)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                if (utteranceId == id) trySend(starts.count { it <= start })
            }

            override fun onDone(utteranceId: String) {
                if (utteranceId != id) return
                trySend(starts.size)
                close()
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) {
                if (utteranceId == id) close()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                if (utteranceId == id) close(SpeechException(SpeechProblem.FAILED))
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                if (utteranceId == id) close(SpeechException(SpeechProblem.FAILED))
            }
        })
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            throw SpeechException(SpeechProblem.FAILED)
        }
        awaitClose { engine.stop() }
    }.flowOn(Dispatchers.Main.immediate)
}
