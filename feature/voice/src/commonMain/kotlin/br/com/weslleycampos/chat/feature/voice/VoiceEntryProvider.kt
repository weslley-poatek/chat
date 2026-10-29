package br.com.weslleycampos.chat.feature.voice

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.feature.chat.palette
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerSheet
import br.com.weslleycampos.chat.feature.voice.speech.rememberMicrophoneAccess
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Single
import org.koin.core.parameter.parametersOf

@Single
class VoiceEntryProvider : EntryProvider {
    override fun serializerModule() = SerializersModule {
        polymorphic(NavKey::class) { subclass(VoiceEntry::class, VoiceEntry.serializer()) }
    }

    override fun entryBuilder(): EntryProviderScope<NavKey>.() -> Unit = {
        entry<VoiceEntry> { key ->
            val viewModel = koinViewModel<VoiceViewModel> { parametersOf(key) }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            // The phases play only with the microphone: asked for when voice mode first shows, and again
            // by a tap on the sphere while it is not allowed.
            val microphone = rememberMicrophoneAccess()
            LaunchedEffect(viewModel, microphone.granted) { viewModel.onMicrophone(microphone.granted) }
            LaunchedEffect(viewModel) { if (!microphone.granted) microphone.request() }
            // The prototype starts the phases on entering voice mode and stops them on leaving it: here
            // while the screen is resumed, so History, the text, another app or a rotation stop them.
            LifecycleResumeEffect(viewModel) {
                viewModel.onStart()
                onPauseOrDispose { viewModel.onStop() }
            }
            val picker = viewModel.picker
            val modelPicker = remember(uiState.models, uiState.favorites, uiState.personas, uiState.choice) {
                uiState.modelPicker
            }
            // Voice wears the accent of what answers the session, in the root's light or dark; a pick
            // changes it at once.
            ChatTheme(palette = uiState.model.palette, darkTheme = ChatTheme.colors.isDark) {
                VoiceScreen(
                    uiState = uiState,
                    onHistoryClick = viewModel::onOpenHistory,
                    onModelClick = picker::onOpen,
                    onSettingsClick = viewModel::onOpenSettings,
                    onSphereTap = { if (microphone.granted) viewModel.onSphereTap() else microphone.request() },
                    onToggleMute = viewModel::onToggleMute,
                    onEnd = viewModel::onEnd,
                    onTextClick = viewModel::onOpenText,
                )
                // The chat sheet with no compare button: voice never compares.
                ModelPickerSheet(
                    open = uiState.modelPickerOpen,
                    state = modelPicker,
                    onDismissRequest = picker::onDismiss,
                    onPick = picker::onPick,
                    onToggleFavorite = picker::onToggleFavorite,
                )
            }
        }
    }
}
