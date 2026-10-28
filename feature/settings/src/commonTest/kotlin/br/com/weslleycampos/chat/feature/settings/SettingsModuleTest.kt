package br.com.weslleycampos.chat.feature.settings

import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.settings.SettingsEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import br.com.weslleycampos.chat.data.settings.AccountRepository
import br.com.weslleycampos.chat.data.settings.ProviderKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
 * `SettingsModule` next to fakes of what the app's other modules provide, so the Koin compiler
 * plugin checks the graph while this compiles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsModuleTest {
    private val koin = koinApplication {
        module<FakeSettingsAppModule>()
        module<SettingsModule>()
    }.koin

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        koin.close()
        Dispatchers.resetMain()
    }

    @Test
    fun koinProvidesTheEntryProvider() {
        assertIs<SettingsEntryProvider>(koin.getAll<EntryProvider>().single())
    }

    @Test
    fun koinBuildsASettingsViewModelOnTheAppsNavigator() {
        val viewModel = koin.get<SettingsViewModel>()

        assertSame(koin.get<Navigator>(), viewModel.navigator)
        viewModel.viewModelScope.cancel()
    }
}

@Module
internal class FakeSettingsAppModule {
    @Single
    fun accounts(): AccountRepository = FakeAccountRepository()

    @Single
    fun keys(): ProviderKeyRepository = FakeProviderKeyRepository()

    @Single
    fun navigator(): Navigator = navigator(VoiceEntry(), SettingsEntry)
}
