package br.com.weslleycampos.chat.feature.voice

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.VoiceRepository
import br.com.weslleycampos.chat.feature.voice.speech.SpeechInput
import br.com.weslleycampos.chat.feature.voice.speech.SpeechOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.plugin.module.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * `VoiceModule` next to fakes of what the app's other modules provide, so the Koin compiler plugin
 * checks the graph while this compiles. The fakes load last: their speech replaces the device's,
 * which needs the Android `Context` the app gives at startup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceModuleTest {
    private val koin = koinApplication {
        module<VoiceModule>()
        module<FakeAppModule>()
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
        assertIs<VoiceEntryProvider>(koin.getAll<EntryProvider>().single())
    }

    @Test
    fun koinProvidesOneSpeechInputAndOutputForTheDevice() {
        assertSame(koin.get<SpeechInput>(), koin.get<SpeechInput>())
        assertSame(koin.get<SpeechOutput>(), koin.get<SpeechOutput>())
    }

    @Test
    fun koinBuildsAVoiceViewModelForTheEntryItIsGivenOnTheAppsNavigator() {
        val first = koin.get<VoiceViewModel> { parametersOf(VoiceEntry(conversationId = 7)) }
        val second = koin.get<VoiceViewModel> { parametersOf(VoiceEntry()) }

        assertEquals<Long?>(7L, first.uiState.value.conversationId)
        assertNull(second.uiState.value.conversationId)
        assertNotSame(first, second)
        assertSame(koin.get<Navigator>(), first.navigator)
    }
}

@Module
internal class FakeAppModule {
    @Single
    fun voices(): VoiceRepository = FakeVoiceRepository()

    @Single
    fun models(): ModelRepository = FakeModelRepository()

    @Single
    fun navigator(): Navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) })

    @Single
    fun input(): SpeechInput = FakeSpeechInput()

    @Single
    fun output(): SpeechOutput = FakeSpeechOutput()
}
