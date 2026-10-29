package br.com.weslleycampos.chat.feature.voice.speech

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/** Whether voice mode may listen, and how to ask. */
@Stable
interface MicrophoneAccess {
    /** The microphone, and speech recognition on iOS, are allowed. */
    val granted: Boolean

    /** Asks the user for them with the system's prompts; [granted] follows the answer. */
    fun request()
}

/**
 * The microphone's access on this platform: Android's `RECORD_AUDIO` permission, iOS's microphone
 * and speech recognition authorizations. [MicrophoneAccess.granted] is read again whenever the
 * screen resumes, so access given in the system's settings counts on return.
 */
@Composable
expect fun rememberMicrophoneAccess(): MicrophoneAccess

/** Access as last read: [granted], and [ask] for the system's prompts. */
internal class GrantedMicrophone(override val granted: Boolean, private val ask: () -> Unit) : MicrophoneAccess {
    override fun request() = ask()
}
