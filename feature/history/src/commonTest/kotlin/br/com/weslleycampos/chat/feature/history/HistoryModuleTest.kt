package br.com.weslleycampos.chat.feature.history

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import br.com.weslleycampos.chat.data.chat.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.koinApplication
import org.koin.plugin.module.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * `HistoryModule` next to fakes of what the app's other modules provide, so the Koin compiler
 * plugin checks the graph while this compiles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryModuleTest {
    private val koin = koinApplication {
        module<FakeAppModule>()
        module<HistoryModule>()
    }.koin

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        koin.close()
        Dispatchers.resetMain()
    }

    @Test
    fun koinProvidesTheEntryProviderAndOneHistoryTimes() {
        assertIs<HistoryEntryProvider>(koin.getAll<EntryProvider>().single())
        assertSame(koin.get<HistoryTimes>(), koin.get<HistoryTimes>())
    }

    @Test
    fun koinBuildsAHistoryViewModelOnTheAppsNavigator() {
        val viewModel = koin.get<HistoryViewModel>()

        assertSame(koin.get<Navigator>(), viewModel.navigator)
    }
}

@Module
internal class FakeAppModule {
    @Single
    fun repository(): ChatRepository = FakeChatRepository()

    @Single
    fun navigator(): Navigator = NavigatorImpl(
        NavBackStack<NavKey>().apply { addAll(listOf(VoiceEntry(), HistoryEntry)) },
    )
}
