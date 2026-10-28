package br.com.weslleycampos.chat.feature.history

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Single

@Single
class HistoryEntryProvider : EntryProvider {
    override fun serializerModule() = SerializersModule {
        polymorphic(NavKey::class) { subclass(HistoryEntry::class, HistoryEntry.serializer()) }
    }

    override fun entryBuilder(): EntryProviderScope<NavKey>.() -> Unit = {
        // History shows no conversation, so it wears the root's accent: the default model's.
        entry<HistoryEntry> {
            val viewModel = koinViewModel<HistoryViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            HistoryScreen(
                uiState = uiState,
                onBack = viewModel::onBack,
                // Settings is row 10.
                onSettingsClick = {},
                onQueryChange = viewModel::onQueryChange,
                onClearQuery = viewModel::onClearQuery,
                onRowClick = viewModel::onRowClick,
                onSwipeStart = viewModel::onSwipeStart,
                onSwipeSettle = viewModel::onSwipeSettle,
                onRowAction = viewModel::onRowAction,
            )
        }
    }
}
