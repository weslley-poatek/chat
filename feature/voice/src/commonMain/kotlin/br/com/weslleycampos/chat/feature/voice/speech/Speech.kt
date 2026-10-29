package br.com.weslleycampos.chat.feature.voice.speech

import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import kotlinx.coroutines.flow.Flow

/**
 * The device's speech recognition: Android's `SpeechRecognizer`, iOS's `SFSpeechRecognizer` on the
 * microphone. It needs the microphone, which [rememberMicrophoneAccess] asks for.
 */
interface SpeechInput {
    /**
     * Listens once, for [languages], the likeliest first: with more than one, each thing said is
     * heard in the language it is in, as far as the device can tell. [Heard.Partial] with the words
     * so far as the user speaks, then one [Heard.Final] with all of them once they stop, and
     * completes. The final words are empty when nothing was heard: silence, or speech it could not
     * make out. The microphone is on while this is collected, and cancelling the collection turns
     * it off.
     *
     * Throws [SpeechException] when it cannot listen at all.
     */
    fun listen(languages: List<VoiceLanguage>): Flow<Heard>
}

/** What [SpeechInput.listen] heard. */
sealed interface Heard {
    /** The user's words so far. */
    val text: String

    /** The user is still speaking. */
    data class Partial(override val text: String) : Heard

    /** The user stopped: everything they said, or nothing. */
    data class Final(override val text: String) : Heard
}

/** The device's speech synthesis: Android's `TextToSpeech`, iOS's `AVSpeechSynthesizer`. */
interface SpeechOutput {
    /**
     * Says [text] aloud in [language]'s voice: as each word starts, how many of its words, split on
     * spaces, have started, then all of them once it has been said, and completes. Cancelling the
     * collection stops it.
     *
     * Throws [SpeechException] when it cannot speak at all.
     */
    fun speak(text: String, language: VoiceLanguage): Flow<Int>
}

/** Why the device cannot listen or speak. */
enum class SpeechProblem {
    /** The microphone, or speech recognition on iOS, was not allowed. */
    NOT_ALLOWED,

    /** The device has no speech recognition or synthesis, or none for the language. */
    UNAVAILABLE,

    /** It failed on the way: no network for a recognizer that needs one, or an audio error. */
    FAILED,
}

/** The device could not listen or speak, for [problem]. */
class SpeechException(val problem: SpeechProblem, cause: Throwable? = null) :
    Exception("Speech failed: $problem", cause)
