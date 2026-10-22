package br.com.weslleycampos.chat.feature.auth.signin

import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.auth.SignInEntry
import br.com.weslleycampos.chat.core.navigation.entries.home.HomeEntry
import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.feature.auth.FakeAuthManager
import br.com.weslleycampos.chat.feature.auth.FakeEmailAuthManager
import br.com.weslleycampos.chat.feature.auth.navigator
import br.com.weslleycampos.chat.feature.auth.resources.FeatureAuthRes
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_email_in_use
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_google
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_google_credential
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_invalid_email
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_network
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_no_account
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_short_password
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_too_many_requests
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_unknown
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_weak_password
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_wrong_credentials
import br.com.weslleycampos.chat.feature.auth.stack
import kotlinx.coroutines.CancellationException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val Email = "you@example.dev"
private const val Password = "correct horse"

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun signInViewModel(
        navigator: NavigatorImpl = navigator(SignInEntry),
        authManager: FakeAuthManager = FakeAuthManager(),
        emailAuthManager: FakeEmailAuthManager = FakeEmailAuthManager(),
    ) = SignInViewModel(navigator = navigator, authManager = authManager, emailAuthManager = emailAuthManager)

    private fun SignInViewModel.fill(email: String = Email, password: String = Password) {
        onEmailChange(email)
        onPasswordChange(password)
    }

    // Modes

    @Test
    fun startsOnSignInWithAnEmptyForm() {
        assertEquals(SignInUiState(), signInViewModel().uiState.value)
    }

    @Test
    fun theModeLinksSwitchModesAndKeepWhatWasTyped() {
        val viewModel = signInViewModel()
        viewModel.fill()
        viewModel.onPasswordVisibilityToggle()

        for (mode in listOf(AuthMode.SignUp, AuthMode.Forgot, AuthMode.SignIn, AuthMode.Forgot, AuthMode.SignIn)) {
            viewModel.onModeChange(mode)

            assertEquals(
                SignInUiState(mode = mode, email = Email, password = Password, showPassword = true),
                viewModel.uiState.value,
            )
        }
    }

    @Test
    fun changingModeClearsTheError() {
        val viewModel = signInViewModel()
        viewModel.onSubmit()

        viewModel.onModeChange(AuthMode.SignUp)

        assertEquals(SignInUiState(mode = AuthMode.SignUp), viewModel.uiState.value)
    }

    @Test
    fun backToSignInFromTheSentConfirmationKeepsTheEmailAndAFreshResetStartsOver() {
        val viewModel = signInViewModel()
        viewModel.onModeChange(AuthMode.Forgot)
        viewModel.onEmailChange(Email)
        viewModel.onSubmit()

        viewModel.onModeChange(AuthMode.SignIn)

        assertEquals(SignInUiState(email = Email), viewModel.uiState.value)
        viewModel.onModeChange(AuthMode.Forgot)
        assertEquals(SignInUiState(mode = AuthMode.Forgot, email = Email), viewModel.uiState.value)
    }

    // Show / hide

    @Test
    fun thePasswordToggleShowsAndHidesThePassword() {
        val viewModel = signInViewModel()

        viewModel.onPasswordVisibilityToggle()
        assertTrue(viewModel.uiState.value.showPassword)

        viewModel.onPasswordVisibilityToggle()
        assertFalse(viewModel.uiState.value.showPassword)
    }

    // Validation

    @Test
    fun anInvalidEmailShowsItsErrorInEveryModeWithoutCallingFirebase() {
        for (mode in AuthMode.entries) {
            for (email in listOf("", "you", "you@example", " @example.dev ")) {
                val emailAuthManager = FakeEmailAuthManager()
                val viewModel = signInViewModel(emailAuthManager = emailAuthManager)
                viewModel.onModeChange(mode)
                viewModel.fill(email = email)

                viewModel.onSubmit()

                assertEquals(
                    SignInUiState(
                        mode = mode,
                        email = email,
                        password = Password,
                        error = FeatureAuthRes.string.sign_in_error_invalid_email,
                    ),
                    viewModel.uiState.value,
                )
                assertEquals(0, emailAuthManager.calls)
            }
        }
    }

    @Test
    fun aShortPasswordOnSignUpAsksForEightCharactersWithoutCallingFirebase() {
        val emailAuthManager = FakeEmailAuthManager()
        val viewModel = signInViewModel(emailAuthManager = emailAuthManager)
        viewModel.onModeChange(AuthMode.SignUp)
        viewModel.fill(password = "1234567")

        viewModel.onSubmit()

        assertEquals(FeatureAuthRes.string.sign_in_error_short_password, viewModel.uiState.value.error)
        assertEquals(0, emailAuthManager.calls)
    }

    @Test
    fun aShortPasswordOnSignInReadsAsAWrongPasswordWithoutCallingFirebase() {
        val emailAuthManager = FakeEmailAuthManager()
        val viewModel = signInViewModel(emailAuthManager = emailAuthManager)
        viewModel.fill(password = "")

        viewModel.onSubmit()

        assertEquals(FeatureAuthRes.string.sign_in_error_wrong_credentials, viewModel.uiState.value.error)
        assertEquals(0, emailAuthManager.calls)
    }

    @Test
    fun typingInEitherFieldClearsTheError() {
        val viewModel = signInViewModel()
        viewModel.onSubmit()

        viewModel.onEmailChange(Email)
        assertEquals(null, viewModel.uiState.value.error)

        viewModel.onSubmit()
        viewModel.onPasswordChange(Password)
        assertEquals(null, viewModel.uiState.value.error)
    }

    // Success

    @Test
    fun aValidSignInSignsInWithWhatWasTypedAndReplacesSignInWithHome() {
        val navigator = navigator(SignInEntry)
        val emailAuthManager = FakeEmailAuthManager()
        val viewModel = signInViewModel(navigator = navigator, emailAuthManager = emailAuthManager)
        viewModel.fill(email = " $Email ", password = " $Password ")

        viewModel.onSubmit()

        assertEquals(listOf(" $Email " to " $Password "), emailAuthManager.signIns)
        assertEquals(listOf(HomeEntry), navigator.stack)
    }

    @Test
    fun aValidSignUpCreatesTheAccountAndReplacesSignInWithHome() {
        val navigator = navigator(SignInEntry)
        val emailAuthManager = FakeEmailAuthManager()
        val viewModel = signInViewModel(navigator = navigator, emailAuthManager = emailAuthManager)
        viewModel.onModeChange(AuthMode.SignUp)
        viewModel.fill(password = "12345678")

        viewModel.onSubmit()

        assertEquals(listOf(Email to "12345678"), emailAuthManager.accountsCreated)
        assertEquals(0, emailAuthManager.signIns.size)
        assertEquals(listOf(HomeEntry), navigator.stack)
    }

    @Test
    fun aValidResetSendsTheLinkWithoutAPasswordAndShowsTheSentConfirmation() {
        val navigator = navigator(SignInEntry)
        val emailAuthManager = FakeEmailAuthManager()
        val viewModel = signInViewModel(navigator = navigator, emailAuthManager = emailAuthManager)
        viewModel.onModeChange(AuthMode.Forgot)
        viewModel.onEmailChange(Email)

        viewModel.onSubmit()

        assertEquals(listOf(Email), emailAuthManager.passwordResets)
        assertEquals(SignInUiState(mode = AuthMode.Forgot, email = Email, forgotSent = true), viewModel.uiState.value)
        assertEquals(listOf(SignInEntry), navigator.stack)
    }

    // Busy

    @Test
    fun theFormIsBusyUntilFirebaseAnswersAndIgnoresAllButTypingMeanwhile() {
        val pending = CompletableDeferred<Unit>()
        val navigator = navigator(SignInEntry)
        val authManager = FakeAuthManager()
        val emailAuthManager = FakeEmailAuthManager().apply { answer = { pending.await() } }
        val viewModel = signInViewModel(
            navigator = navigator,
            authManager = authManager,
            emailAuthManager = emailAuthManager,
        )
        viewModel.fill()
        var pickerOpened = false

        viewModel.onSubmit()
        viewModel.onSubmit()
        viewModel.onModeChange(AuthMode.SignUp)
        viewModel.onGoogleSignInClick { pickerOpened = true }
        viewModel.signInWithGoogle("id-token", null)
        viewModel.onPasswordVisibilityToggle()

        assertEquals(
            SignInUiState(email = Email, password = Password, showPassword = true, busy = AuthBusy.Form),
            viewModel.uiState.value,
        )
        assertEquals(1, emailAuthManager.calls)
        assertFalse(pickerOpened)
        assertEquals(emptyList(), authManager.googleSignIns)
        assertEquals(listOf(SignInEntry), navigator.stack)

        pending.complete(Unit)

        assertEquals(listOf(HomeEntry), navigator.stack)
    }

    @Test
    fun aResetIsBusyUntilFirebaseAnswers() {
        val pending = CompletableDeferred<Unit>()
        val emailAuthManager = FakeEmailAuthManager().apply { answer = { pending.await() } }
        val viewModel = signInViewModel(emailAuthManager = emailAuthManager)
        viewModel.onModeChange(AuthMode.Forgot)
        viewModel.onEmailChange(Email)

        viewModel.onSubmit()
        val reset = SignInUiState(mode = AuthMode.Forgot, email = Email)
        assertEquals(reset.copy(busy = AuthBusy.Form), viewModel.uiState.value)

        pending.complete(Unit)
        assertEquals(reset.copy(forgotSent = true), viewModel.uiState.value)
    }

    @Test
    fun aCancelledCallLeavesTheFormUsable() {
        val emailAuthManager = FakeEmailAuthManager().apply {
            answer = { throw CancellationException("task cancelled") }
        }
        val viewModel = signInViewModel(emailAuthManager = emailAuthManager)
        viewModel.fill()

        viewModel.onSubmit()

        assertEquals(SignInUiState(email = Email, password = Password), viewModel.uiState.value)
    }

    // Failures

    @Test
    fun eachFirebaseFailureShowsItsMessageAndStaysOnTheForm() {
        val invalidEmail = AuthException.InvalidEmail("bad", null)
        val wrongPassword = AuthException.InvalidCredentials("wrong", null)
        val tooMany = AuthException.TooManyRequests("slow down", null)
        val offline = AuthException.NetworkError("offline", null)
        val unknown = AuthException.Unknown("boom", null)
        val expectedMessages = with(FeatureAuthRes.string) {
            listOf(
                Triple(AuthMode.SignIn, invalidEmail, sign_in_error_invalid_email),
                Triple(AuthMode.SignIn, wrongPassword, sign_in_error_wrong_credentials),
                Triple(AuthMode.SignIn, AuthException.UserNotFound("none", null), sign_in_error_wrong_credentials),
                Triple(AuthMode.SignIn, tooMany, sign_in_error_too_many_requests),
                Triple(AuthMode.SignIn, offline, sign_in_error_network),
                Triple(AuthMode.SignIn, unknown, sign_in_error_unknown),
                Triple(AuthMode.SignIn, IllegalStateException("not Firebase"), sign_in_error_unknown),
                Triple(AuthMode.SignUp, invalidEmail, sign_in_error_invalid_email),
                Triple(AuthMode.SignUp, AuthException.EmailAlreadyInUse("taken", null), sign_in_error_email_in_use),
                Triple(AuthMode.SignUp, AuthException.WeakPassword("weak", null), sign_in_error_weak_password),
                Triple(AuthMode.SignUp, tooMany, sign_in_error_too_many_requests),
                Triple(AuthMode.SignUp, offline, sign_in_error_network),
                Triple(AuthMode.SignUp, unknown, sign_in_error_unknown),
                Triple(AuthMode.Forgot, invalidEmail, sign_in_error_invalid_email),
                Triple(AuthMode.Forgot, AuthException.UserNotFound("none", null), sign_in_error_no_account),
                Triple(AuthMode.Forgot, tooMany, sign_in_error_too_many_requests),
                Triple(AuthMode.Forgot, offline, sign_in_error_network),
                Triple(AuthMode.Forgot, unknown, sign_in_error_unknown),
            )
        }
        for ((mode, failure, message) in expectedMessages) {
            val navigator = navigator(SignInEntry)
            val viewModel = signInViewModel(navigator = navigator, emailAuthManager = FakeEmailAuthManager(failure))
            viewModel.onModeChange(mode)
            viewModel.fill()

            viewModel.onSubmit()

            assertEquals(
                SignInUiState(mode = mode, email = Email, password = Password, error = message),
                viewModel.uiState.value,
                "$mode, ${failure::class.simpleName}",
            )
            assertEquals(listOf(SignInEntry), navigator.stack)
        }
    }

    @Test
    fun aFailedSignInCanBeTriedAgain() {
        val navigator = navigator(SignInEntry)
        val emailAuthManager = FakeEmailAuthManager(failure = AuthException.NetworkError("offline", null))
        val viewModel = signInViewModel(navigator = navigator, emailAuthManager = emailAuthManager)
        viewModel.fill()
        viewModel.onSubmit()

        emailAuthManager.answer = {}
        viewModel.onSubmit()

        assertEquals(2, emailAuthManager.signIns.size)
        assertEquals(listOf(HomeEntry), navigator.stack)
    }

    // Google

    @Test
    fun theGoogleButtonOpensThePickerWhileNothingIsBusy() {
        val viewModel = signInViewModel()
        var pickerOpened = 0

        viewModel.onGoogleSignInClick { pickerOpened++ }

        assertEquals(1, pickerOpened)
    }

    @Test
    fun aGoogleTokenShowsAuthorizingUntilFirebaseAnswersThenReplacesSignInWithHome() {
        val pending = CompletableDeferred<Unit>()
        val navigator = navigator(SignInEntry)
        val authManager = FakeAuthManager().apply { authenticate = { pending.await() } }
        val viewModel = signInViewModel(navigator = navigator, authManager = authManager)

        viewModel.signInWithGoogle("id-token", "access-token")

        assertEquals(SignInUiState(busy = AuthBusy.Google), viewModel.uiState.value)
        assertEquals(listOf<Pair<String, String?>>("id-token" to "access-token"), authManager.googleSignIns)
        assertEquals(listOf(SignInEntry), navigator.stack)

        pending.complete(Unit)

        assertEquals(listOf(HomeEntry), navigator.stack)
    }

    @Test
    fun aGoogleTokenThatFailsShowsItsMessageAndKeepsWhatWasTyped() {
        val expectedMessages = mapOf(
            AuthException.NetworkError("offline", null) to FeatureAuthRes.string.sign_in_error_network,
            AuthException.InvalidCredentials("rejected", null) to FeatureAuthRes.string.sign_in_error_google_credential,
            AuthException.TooManyRequests("slow down", null) to FeatureAuthRes.string.sign_in_error_google,
            AuthException.Unknown("boom", null) to FeatureAuthRes.string.sign_in_error_google,
        )
        for ((failure, message) in expectedMessages) {
            val navigator = navigator(SignInEntry)
            val viewModel = signInViewModel(navigator = navigator, authManager = FakeAuthManager(failure = failure))
            viewModel.fill()

            viewModel.signInWithGoogle("id-token", null)

            assertEquals(SignInUiState(email = Email, password = Password, error = message), viewModel.uiState.value)
            assertEquals(listOf(SignInEntry), navigator.stack)
        }
    }

    @Test
    fun aGoogleTokenClearsTheEarlierError() {
        val pending = CompletableDeferred<Unit>()
        val authManager = FakeAuthManager().apply { authenticate = { pending.await() } }
        val viewModel = signInViewModel(authManager = authManager)
        viewModel.onSubmit()

        viewModel.signInWithGoogle("id-token", null)

        assertEquals(SignInUiState(busy = AuthBusy.Google), viewModel.uiState.value)
        pending.complete(Unit)
    }

    @Test
    fun aPickerFailureShowsTheGenericGoogleMessage() {
        val viewModel = signInViewModel()

        viewModel.onGoogleSignInError(IllegalStateException("no Google account on the device"))

        assertEquals(SignInUiState(error = FeatureAuthRes.string.sign_in_error_google), viewModel.uiState.value)
    }

    @Test
    fun aCancelledPickerIsRethrownNotShown() {
        val viewModel = signInViewModel()

        assertFailsWith<CancellationException> { viewModel.onGoogleSignInError(CancellationException("left")) }

        assertEquals(SignInUiState(), viewModel.uiState.value)
    }

    @Test
    fun aTokenThatArrivesWhileSigningInIsIgnored() {
        val pending = CompletableDeferred<Unit>()
        val authManager = FakeAuthManager().apply { authenticate = { pending.await() } }
        val viewModel = signInViewModel(authManager = authManager)
        var pickerOpened = false

        viewModel.signInWithGoogle("first", null)
        viewModel.signInWithGoogle("second", null)
        viewModel.onGoogleSignInClick { pickerOpened = true }
        viewModel.onSubmit()

        assertEquals(SignInUiState(busy = AuthBusy.Google), viewModel.uiState.value)
        assertEquals(listOf<Pair<String, String?>>("first" to null), authManager.googleSignIns)
        assertFalse(pickerOpened)
        pending.complete(Unit)
    }
}
