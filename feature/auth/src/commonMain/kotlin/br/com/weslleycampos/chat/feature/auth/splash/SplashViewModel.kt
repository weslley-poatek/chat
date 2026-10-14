package br.com.weslleycampos.chat.feature.auth.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.auth.SignInEntry
import br.com.weslleycampos.chat.core.navigation.entries.auth.SplashEntry
import br.com.weslleycampos.chat.core.navigation.entries.home.HomeEntry
import br.com.weslleycampos.chat.data.auth.AuthManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/** The session gate: replaces Splash with Home when Firebase restores a user, and with SignIn otherwise. */
@KoinViewModel(binds = [SplashViewModel::class])
class SplashViewModel(
    val navigator: Navigator,
    private val authManager: AuthManager,
) : ViewModel(), Navigator by navigator {
    init {
        viewModelScope.launch {
            val entry = if (authManager.currentUser.first() == null) SignInEntry else HomeEntry
            navigate(entry) { popUpTo<SplashEntry> { inclusive = true } }
        }
    }
}
