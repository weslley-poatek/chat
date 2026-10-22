package br.com.weslleycampos.chat

import androidx.compose.ui.window.ComposeUIViewController
import br.com.weslleycampos.chat.core.navigation.utils.EntriesAggregator
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    AppHost(entries = KoinPlatform.getKoin().get<EntriesAggregator>())
}
