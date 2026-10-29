package br.com.weslleycampos.chat.feature.voice.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** `RECORD_AUDIO`, asked for with the activity's permission prompt. */
@Composable
actual fun rememberMicrophoneAccess(): MicrophoneAccess {
    val context = LocalContext.current
    var granted by remember(context) { mutableStateOf(context.hasMicrophone()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LifecycleResumeEffect(context) {
        granted = context.hasMicrophone()
        onPauseOrDispose {}
    }
    return remember(granted, launcher) {
        GrantedMicrophone(granted = granted, ask = { launcher.launch(Manifest.permission.RECORD_AUDIO) })
    }
}

private fun Context.hasMicrophone(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
