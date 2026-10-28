package br.com.weslleycampos.chat.feature.settings

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import br.com.weslleycampos.chat.core.navigation.entries.settings.SettingsEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Single

@Single
class SettingsEntryProvider : EntryProvider {
    override fun serializerModule() = SerializersModule {
        polymorphic(NavKey::class) { subclass(SettingsEntry::class, SettingsEntry.serializer()) }
    }

    override fun entryBuilder(): EntryProviderScope<NavKey>.() -> Unit = {
        // Settings shows no conversation, so it wears the root's accent: the default model's.
        entry<SettingsEntry> {
            val viewModel = koinViewModel<SettingsViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val draft by viewModel.draft.collectAsStateWithLifecycle()
            // While the account is being deleted, system back and the iOS edge swipe stay here, as the
            // chevron does: the delete runs to its end and then leaves for sign-up itself.
            NavigationBackHandler(
                state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
                isBackEnabled = uiState.deleting,
                onBackCompleted = {},
            )
            SettingsScreen(
                uiState = uiState,
                draft = draft,
                appVersion = rememberAppVersion(),
                onBack = viewModel::onBack,
                onSignOut = viewModel::onSignOut,
                onDeleteAccount = viewModel::onDeleteAccount,
                onKeyRowClick = viewModel::onKeyRowClick,
                onDraftChange = viewModel::onDraftChange,
                onSaveKey = viewModel::onSaveKey,
                onRemoveKey = viewModel::onRemoveKey,
            )
        }
    }
}
