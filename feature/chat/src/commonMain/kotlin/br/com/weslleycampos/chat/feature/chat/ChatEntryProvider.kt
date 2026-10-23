package br.com.weslleycampos.chat.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerCompareButton
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
            val copyToClipboard: (String) -> Unit = remember(clipboard, scope) {
                fun(text: String) {
                    scope.launch { clipboard.setClipEntry(plainTextClipEntry(text)) }
                }
            }
            val actions = viewModel.messageActions
            val compare = viewModel.compareActions
            val messageActions = remember(actions, compare, copyToClipboard) {
                MessageActionCallbacks(
                    onSelect = actions::onSelect,
                    onCopy = { message -> actions.onCopy(message, copyToClipboard) },
                    onFork = actions::onFork,
                    onReadAloud = actions::onReadAloud,
                    onRetry = actions::onRetry,
                    onPlayPause = actions.player::onPlayPause,
                    onStopReading = actions.player::onStop,
                    onContinueWith = compare::onContinueWith,
                )
            }
            // Leaving the chat, for History, another app or a rotation, closes the action row and
            // pauses a read, as the prototype's `go()` clears the selection.
            LifecycleStartEffect(actions) { onStopOrDispose { actions.onLeave() } }
            val modelPicker = remember(uiState.favorites, uiState.personas, uiState.choice) { uiState.modelPicker }
            // The chat wears the accent of what answers it over the root's, which is the default
            // model's, in the root's light or dark; a pick changes it at once.
            ChatTheme(palette = uiState.model.palette, darkTheme = ChatTheme.colors.isDark) {
                ChatScreen(
                    uiState = uiState,
                    onHistoryClick = viewModel::onOpenHistory,
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
                    onDismissForkBanner = actions::onDismissForkBanner,
                    messageActions = messageActions,
                )
                ModelPickerSheet(
                    open = uiState.modelPickerOpen,
                    state = modelPicker,
                    onDismissRequest = viewModel::onDismissModelPicker,
                    onPick = viewModel::onPick,
                    onToggleFavorite = viewModel::onToggleFavorite,
                    headerAction = {
                        if (modelPicker.showsCompare) {
                            ModelPickerCompareButton(on = modelPicker.compareOn, onClick = compare::onToggleCompare)
                        }
                    },
                )
            }
        }
    }
}
