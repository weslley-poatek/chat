package br.com.weslleycampos.chat.feature.auth.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.auth.SignInEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthManager
import br.com.weslleycampos.chat.data.auth.EmailAuthManager
import br.com.weslleycampos.chat.feature.auth.resources.FeatureAuthRes
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_email_in_use
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_google
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_google_credential
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_invalid_email
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_network
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_no_account
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_too_many_requests
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_unknown
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_weak_password
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_wrong_credentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.KoinViewModel

/**
 * The Auth frame's logic: the prototype's `authSubmit`, `oauth` and mode links, on Firebase.
 *
 * While [SignInUiState.busy] is set, a second submit, the Google button and the mode links are
 * ignored, so each answer lands in the mode that asked for it. Typing and show/hide stay live.
 */
@KoinViewModel(binds = [SignInViewModel::class])
class SignInViewModel(
    val navigator: Navigator,
    private val authManager: AuthManager,
    private val emailAuthManager: EmailAuthManager,
) : ViewModel(), Navigator by navigator {
    val uiState: StateFlow<SignInUiState>
        field = MutableStateFlow(SignInUiState())

    fun onEmailChange(email: String) = uiState.update { it.copy(email = email, error = null) }

    fun onPasswordChange(password: String) = uiState.update { it.copy(password = password, error = null) }

    fun onPasswordVisibilityToggle() = uiState.update { it.copy(showPassword = !it.showPassword) }

    /** The mode links, "← sign in", "Back to sign in" and system back. Keeps what was typed. */
    fun onModeChange(mode: AuthMode) {
        if (uiState.value.busy != null) return
        uiState.update { it.copy(mode = mode, forgotSent = false, error = null) }
    }

    fun onSubmit() {
        val state = uiState.value
        if (state.busy != null) return
        val invalid = state.validationError()
        if (invalid != null) {
            uiState.update { it.copy(error = invalid) }
            return
        }
        uiState.update { it.copy(busy = AuthBusy.Form, error = null) }
        viewModelScope.launch {
            try {
                when (state.mode) {
                    AuthMode.SignIn -> {
                        emailAuthManager.signIn(state.email, state.password)
                        navigateHome()
                    }
                    AuthMode.SignUp -> {
                        emailAuthManager.createAccount(state.email, state.password)
                        navigateHome()
                    }
                    AuthMode.Forgot -> {
                        emailAuthManager.sendPasswordReset(state.email)
                        uiState.update { it.copy(busy = null, forgotSent = true) }
                    }
                }
            } catch (e: CancellationException) {
                // A cancelled Firebase task arrives as cancellation too; never leave the form stuck busy.
                uiState.update { it.copy(busy = null) }
                throw e
            } catch (e: Exception) {
                uiState.update { it.copy(busy = null, error = e.emailAuthMessage(state.mode)) }
            }
        }
    }

    /** Opens the Google account picker through [launch], unless the screen is already waiting. */
    fun onGoogleSignInClick(launch: () -> Unit) {
        if (uiState.value.busy == null) launch()
    }

    fun signInWithGoogle(idToken: String, accessToken: String?) {
        if (uiState.value.busy != null) return
        uiState.update { it.copy(busy = AuthBusy.Google, error = null) }
        viewModelScope.launch {
            try {
                authManager.signInWithGoogle(idToken, accessToken)
                navigateHome()
            } catch (e: CancellationException) {
                uiState.update { it.copy(busy = null) }
                throw e
            } catch (e: Exception) {
                onGoogleSignInError(e)
            }
        }
    }

    fun onGoogleSignInError(error: Throwable) {
        if (error is CancellationException) throw error
        uiState.update { it.copy(busy = null, error = error.googleSignInMessage()) }
    }

    /**
     * Signed in: Voice, the home screen, replaces SignIn as the root of the back stack, where the
     * prototype's `finishAuth` lands on an empty Chat (the user made Voice the home screen).
     */
    private fun navigateHome() = navigate(VoiceEntry()) { popUpTo<SignInEntry> { inclusive = true } }
}

/** With email enumeration protection off, only a reset reports a missing account as such. */
private fun Throwable.emailAuthMessage(mode: AuthMode): StringResource = when (this) {
    is AuthException.InvalidEmail -> FeatureAuthRes.string.sign_in_error_invalid_email
    is AuthException.InvalidCredentials -> FeatureAuthRes.string.sign_in_error_wrong_credentials
    is AuthException.UserNotFound -> if (mode == AuthMode.Forgot) {
        FeatureAuthRes.string.sign_in_error_no_account
    } else {
        FeatureAuthRes.string.sign_in_error_wrong_credentials
    }
    is AuthException.EmailAlreadyInUse -> FeatureAuthRes.string.sign_in_error_email_in_use
    is AuthException.WeakPassword -> FeatureAuthRes.string.sign_in_error_weak_password
    is AuthException.TooManyRequests -> FeatureAuthRes.string.sign_in_error_too_many_requests
    is AuthException.NetworkError -> FeatureAuthRes.string.sign_in_error_network
    else -> FeatureAuthRes.string.sign_in_error_unknown
}

private fun Throwable.googleSignInMessage(): StringResource = when (this) {
    is AuthException.NetworkError -> FeatureAuthRes.string.sign_in_error_network
    is AuthException.InvalidCredentials -> FeatureAuthRes.string.sign_in_error_google_credential
    else -> FeatureAuthRes.string.sign_in_error_google
}
