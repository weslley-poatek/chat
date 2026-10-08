package br.com.weslleycampos.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

@Composable
fun App(
    navBackStack: NavBackStack<NavKey>,
    entryBuilders: List<EntryProviderScope<NavKey>.() -> Unit>,
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
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
