package br.com.weslleycampos.chat.feature.chat

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import br.com.weslleycampos.chat.data.chat.ChatRepository
import br.com.weslleycampos.chat.data.chat.ModelRepository
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
 * `ChatModule` next to fakes of what the app's other modules provide, so the Koin compiler plugin
 * checks the graph while this compiles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatModuleTest {
    private val koin = koinApplication {
        module<FakeAppModule>()
        module<ChatModule>()
    }.koin

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() {
        koin.close()
        Dispatchers.resetMain()
    }

    @Test
    fun koinProvidesTheEntryProviderAndOneChatTimes() {
        assertIs<ChatEntryProvider>(koin.getAll<EntryProvider>().single())
        assertSame(koin.get<ChatTimes>(), koin.get<ChatTimes>())
    }

    @Test
    fun koinBuildsAChatViewModelForTheEntryItIsGiven() {
        val first = koin.get<ChatViewModel> { parametersOf(ChatEntry(conversationId = 7)) }
        val second = koin.get<ChatViewModel> { parametersOf(ChatEntry()) }

        assertEquals<Long?>(7L, first.uiState.value.conversationId)
        assertNull(second.uiState.value.conversationId)
        assertNotSame(first, second)
        assertSame(koin.get<Navigator>(), first.navigator)
    }
}

@Module
internal class FakeAppModule {
    @Single
    fun repository(): ChatRepository = FakeChatRepository()

    @Single
    fun models(): ModelRepository = FakeModelRepository()

    @Single
    fun navigator(): Navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(ChatEntry()) })
}
