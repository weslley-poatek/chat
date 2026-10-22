package br.com.weslleycampos.chat.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerSheet
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Single
import org.koin.core.parameter.parametersOf

@Single
class ChatEntryProvider : EntryProvider {
    override fun serializerModule() = SerializersModule {
        polymorphic(NavKey::class) { subclass(ChatEntry::class, ChatEntry.serializer()) }
    }

    override fun entryBuilder(): EntryProviderScope<NavKey>.() -> Unit = {
        entry<ChatEntry> { key ->
            val viewModel = koinViewModel<ChatViewModel> { parametersOf(key) }
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val clipboard = LocalClipboard.current
            val scope = rememberCoroutineScope()
            val copyToClipboard: (String) -> Unit = { text ->
                scope.launch { clipboard.setClipEntry(plainTextClipEntry(text)) }
            }
            val modelPicker = remember(uiState.favorites, uiState.personas, uiState.choice) { uiState.modelPicker }
            // The chat wears the accent of what answers it over the root's, which is the default
            // model's, in the root's light or dark; a pick changes it at once.
            ChatTheme(palette = uiState.model.palette, darkTheme = ChatTheme.colors.isDark) {
                ChatScreen(
                    uiState = uiState,
                    // History is row 5.
                    onHistoryClick = {},
                    onModelClick = viewModel::onOpenModelPicker,
                    onNewChat = viewModel::onNewChat,
                    onOpenRecent = viewModel::onOpenRecent,
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::onSend,
                    // The attach sheet is row 8.
                    onAttachClick = {},
                    // Voice mode is row 11.
                    onVoiceClick = {},
                    onCopyCode = { block -> viewModel.onCopyCode(block, copyToClipboard) },
                )
                // Row 7 adds the compare button to the sheet's `headerAction`.
                ModelPickerSheet(
                    open = uiState.modelPickerOpen,
                    state = modelPicker,
                    onDismissRequest = viewModel::onDismissModelPicker,
                    onPick = viewModel::onPick,
                    onToggleFavorite = viewModel::onToggleFavorite,
                )
            }
        }
    }
}
