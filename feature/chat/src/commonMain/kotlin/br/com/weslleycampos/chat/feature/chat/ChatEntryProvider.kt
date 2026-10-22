package br.com.weslleycampos.chat.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import br.com.weslleycampos.chat.core.ui.ChatTheme
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
            // The accent follows the conversation's model. Row 4 moves this to the app's root theme.
            ChatTheme(palette = uiState.model.palette) {
                ChatScreen(
                    uiState = uiState,
                    // History is row 5.
                    onHistoryClick = {},
                    // The model picker is row 4.
                    onModelClick = {},
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
            }
        }
    }
}
