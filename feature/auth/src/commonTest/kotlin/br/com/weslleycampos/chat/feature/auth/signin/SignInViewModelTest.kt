package br.com.weslleycampos.chat.feature.auth.signin

import br.com.weslleycampos.chat.core.navigation.entries.auth.SignInEntry
import br.com.weslleycampos.chat.core.navigation.entries.home.HomeEntry
import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.feature.auth.FakeAuthManager
import br.com.weslleycampos.chat.feature.auth.navigator
import br.com.weslleycampos.chat.feature.auth.resources.FeatureAuthRes
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_google_credential_error
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_google_error
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_google_network_error
import br.com.weslleycampos.chat.feature.auth.stack
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aTokenThatSignsInReplacesSignInWithHome() {
        val navigator = navigator(SignInEntry)
        val authManager = FakeAuthManager()
        val viewModel = SignInViewModel(navigator, authManager)

        viewModel.signInWithGoogle("id-token", "access-token")

        assertEquals(listOf<Pair<String, String?>>("id-token" to "access-token"), authManager.googleSignIns)
        assertEquals(listOf(HomeEntry), navigator.stack)
    }

    @Test
    fun aTokenThatFailsShowsItsMessageAndStaysOnSignIn() {
        val expectedMessages = mapOf(
            AuthException.NetworkError("offline", null) to FeatureAuthRes.string.sign_in_google_network_error,
            AuthException.InvalidCredentials("rejected", null) to FeatureAuthRes.string.sign_in_google_credential_error,
            AuthException.Unknown("boom", null) to FeatureAuthRes.string.sign_in_google_error,
        )
        for ((failure, message) in expectedMessages) {
            val navigator = navigator(SignInEntry)
            val viewModel = SignInViewModel(navigator, FakeAuthManager(failure = failure))

            viewModel.signInWithGoogle("id-token", null)

            assertEquals(SignInUiState(error = message), viewModel.uiState.value)
            assertEquals(listOf(SignInEntry), navigator.stack)
        }
    }

    @Test
    fun aPickerFailureShowsTheGenericMessage() {
        val viewModel = SignInViewModel(navigator(SignInEntry), FakeAuthManager())

        viewModel.onGoogleSignInError(IllegalStateException("no Google account on the device"))

        assertEquals(SignInUiState(error = FeatureAuthRes.string.sign_in_google_error), viewModel.uiState.value)
    }

    @Test
    fun aTokenThatArrivesWhileSigningInIsIgnored() {
        val pending = CompletableDeferred<Unit>()
        val authManager = FakeAuthManager().apply { authenticate = { pending.await() } }
        val viewModel = SignInViewModel(navigator(SignInEntry), authManager)

        viewModel.signInWithGoogle("first", null)
        viewModel.signInWithGoogle("second", null)

        assertEquals(SignInUiState(loading = true), viewModel.uiState.value)
        assertEquals(listOf<Pair<String, String?>>("first" to null), authManager.googleSignIns)
        pending.complete(Unit)
    }
}
