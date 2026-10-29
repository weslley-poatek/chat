package br.com.weslleycampos.chat.feature.voice.speech

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** The microphone and speech recognition, asked for one after the other with the system's prompts. */
@Composable
actual fun rememberMicrophoneAccess(): MicrophoneAccess {
    var granted by remember { mutableStateOf(speechAllowed()) }
    LifecycleResumeEffect(Unit) {
        granted = speechAllowed()
        onPauseOrDispose {}
    }
    return remember(granted) {
        GrantedMicrophone(granted = granted) {
            SFSpeechRecognizer.requestAuthorization { _ ->
                AVAudioSession.sharedInstance().requestRecordPermission { _ ->
                    dispatch_async(dispatch_get_main_queue()) { granted = speechAllowed() }
                }
            }
        }
    }
}

/** Speech recognition and the microphone are both allowed. */
internal fun speechAllowed(): Boolean =
    SFSpeechRecognizer.authorizationStatus() ==
        SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized &&
        AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted
