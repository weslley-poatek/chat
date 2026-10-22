package br.com.weslleycampos.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import br.com.weslleycampos.chat.core.navigation.entries.auth.SplashEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntriesAggregator
import br.com.weslleycampos.chat.core.navigation.utils.rememberNavigator
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import org.koin.compose.viewmodel.koinViewModel

/**
 * What both hosts show: the back stack from [SplashEntry], saved across configuration changes and
 * process death, in the accent of the default model that [AppViewModel] reads. It needs Koin;
 * previews and tests use [App].
 */
@Composable
fun AppHost(entries: EntriesAggregator, modifier: Modifier = Modifier) {
    val navigator = rememberNavigator(SplashEntry)
    val viewModel = koinViewModel<AppViewModel>()
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    val entryBuilders = remember(entries) { entries.entries.map { it.entryBuilder() } }
    App(navBackStack = navigator.navBackStack, entryBuilders = entryBuilders, palette = palette, modifier = modifier)
}

/**
 * The back stack in a `NavDisplay`, in the root theme: [palette] is the accent of every screen that
 * shows no conversation. A chat wears its own model's over it.
 */
@Composable
fun App(
    navBackStack: NavBackStack<NavKey>,
    entryBuilders: List<EntryProviderScope<NavKey>.() -> Unit>,
    modifier: Modifier = Modifier,
    palette: ChatPalette = ChatPalette.Default,
) {
    ChatTheme(palette = palette) {
        NavDisplay(
            backStack = navBackStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entryBuilders.forEach { builder -> this.builder() }
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}
