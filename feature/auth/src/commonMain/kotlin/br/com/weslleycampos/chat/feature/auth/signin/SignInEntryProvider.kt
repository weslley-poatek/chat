package br.com.weslleycampos.chat.feature.auth.signin

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import br.com.weslleycampos.chat.core.navigation.entries.auth.SignInEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Single

@Single
class SignInEntryProvider : EntryProvider {
    override fun serializerModule() = SerializersModule {
        polymorphic(NavKey::class) { subclass(SignInEntry::class, SignInEntry.serializer()) }
    }

    override fun entryBuilder(): EntryProviderScope<NavKey>.() -> Unit = {
        entry<SignInEntry> {
            val viewModel = koinViewModel<SignInViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val launchGoogleSignIn = rememberGoogleSignInLauncher(
                onToken = viewModel::signInWithGoogle,
                onError = viewModel::onGoogleSignInError,
            )
            // Sign up and the reset are modes of this one entry, so system back returns to sign in
            // from them, as "← sign in" does, instead of leaving the screen.
            NavigationBackHandler(
                state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
                isBackEnabled = uiState.showsBackToSignIn,
                onBackCompleted = { viewModel.onModeChange(AuthMode.SignIn) },
            )
            SignInScreen(
                uiState = uiState,
                onGoogleSignInClick = { viewModel.onGoogleSignInClick(launchGoogleSignIn) },
                onEmailChange = viewModel::onEmailChange,
                onPasswordChange = viewModel::onPasswordChange,
                onPasswordVisibilityToggle = viewModel::onPasswordVisibilityToggle,
                onModeChange = viewModel::onModeChange,
                onSubmit = viewModel::onSubmit,
            )
        }
    }
}
