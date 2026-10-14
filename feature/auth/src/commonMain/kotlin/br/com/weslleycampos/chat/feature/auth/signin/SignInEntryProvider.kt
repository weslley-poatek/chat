package br.com.weslleycampos.chat.feature.auth.signin

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
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
            SignInScreen(
                uiState = uiState,
                onGoogleSignInClick = rememberGoogleSignInLauncher(
                    onToken = viewModel::signInWithGoogle,
                    onError = viewModel::onGoogleSignInError,
                ),
            )
        }
    }
}
