package br.com.weslleycampos.chat.feature.auth.splash

import br.com.weslleycampos.chat.core.navigation.entries.auth.SignInEntry
import br.com.weslleycampos.chat.core.navigation.entries.auth.SplashEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.feature.auth.FakeAuthManager
import br.com.weslleycampos.chat.feature.auth.navigator
import br.com.weslleycampos.chat.feature.auth.stack
import br.com.weslleycampos.chat.feature.auth.user
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aRestoredUserReplacesSplashWithVoiceTheRootThatBackLeavesTheAppFrom() {
        val navigator = navigator(SplashEntry)

        SplashViewModel(navigator, FakeAuthManager(user = user()))

        assertEquals(listOf(VoiceEntry()), navigator.stack)
        // Voice is the root: there is nothing to go back to, so system back leaves the app.
        assertFalse(navigator.navigateUp())
        assertEquals(listOf(VoiceEntry()), navigator.stack)
    }

    @Test
    fun noUserReplacesSplashWithSignIn() {
        val navigator = navigator(SplashEntry)

        SplashViewModel(navigator, FakeAuthManager())

        assertEquals(listOf(SignInEntry), navigator.stack)
    }
}
