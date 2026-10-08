package br.com.weslleycampos.chat

import androidx.compose.ui.window.ComposeUIViewController
import br.com.weslleycampos.chat.core.navigation.entries.home.HomeEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntriesAggregator
import br.com.weslleycampos.chat.core.navigation.utils.rememberNavigator
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    val entries = KoinPlatform.getKoin().get<EntriesAggregator>().entries
    val navigator = rememberNavigator(HomeEntry)

    App(
        navBackStack = navigator.navBackStack,
        entryBuilders = entries.map { it.entryBuilder() },
    )
}
