package br.com.weslleycampos.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.feature.chat.palette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

/**
 * The root's accent: the palette of the default model, the one a new chat starts on. Every screen
 * that shows no conversation (Splash, sign-in and History now; Settings later) wears it,
 * and changes with it at once; the chat wears its own model's over it. [ChatPalette.Default] until
 * the default model is read.
 */
@KoinViewModel
class AppViewModel(models: ModelRepository) : ViewModel() {
    val palette: StateFlow<ChatPalette> = models.observeDefaultModel()
        .map { it.palette }
        .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = ChatPalette.Default)
}
