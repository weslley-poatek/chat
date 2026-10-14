package br.com.weslleycampos.chat.feature.auth.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.auth.SignInEntry
import br.com.weslleycampos.chat.core.navigation.entries.home.HomeEntry
import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthManager
import br.com.weslleycampos.chat.feature.auth.resources.FeatureAuthRes
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_google_credential_error
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_google_error
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_google_network_error
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel(binds = [SignInViewModel::class])
class SignInViewModel(
    val navigator: Navigator,
    private val authManager: AuthManager,
) : ViewModel(), Navigator by navigator {
    val uiState: StateFlow<SignInUiState>
        field = MutableStateFlow(SignInUiState())

    fun signInWithGoogle(idToken: String, accessToken: String?) {
        if (uiState.value.loading) return
        uiState.value = SignInUiState(loading = true)
        viewModelScope.launch {
            try {
                authManager.signInWithGoogle(idToken, accessToken)
                navigate(HomeEntry) { popUpTo<SignInEntry> { inclusive = true } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onGoogleSignInError(e)
            }
        }
    }

    fun onGoogleSignInError(error: Throwable) {
        if (error is CancellationException) throw error
        val message = when (error) {
            is AuthException.NetworkError -> FeatureAuthRes.string.sign_in_google_network_error
            is AuthException.InvalidCredentials -> FeatureAuthRes.string.sign_in_google_credential_error
            else -> FeatureAuthRes.string.sign_in_google_error
        }
        uiState.value = SignInUiState(error = message)
    }
}
