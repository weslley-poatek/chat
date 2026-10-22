package br.com.weslleycampos.chat.feature.auth

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.data.auth.AuthManager
import br.com.weslleycampos.chat.data.auth.AuthUser
import br.com.weslleycampos.chat.data.auth.EmailAuthManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class FakeAuthManager(
    user: AuthUser? = null,
    private val failure: Exception? = null,
) : AuthManager {
    override val currentUser: Flow<AuthUser?> = flowOf(user)
    val googleSignIns = mutableListOf<Pair<String, String?>>()
    var authenticate: suspend () -> Unit = { failure?.let { throw it } }

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?) {
        googleSignIns += idToken to accessToken
        authenticate()
    }

    override fun signOut() = Unit
}

/** Records every call; [answer] decides how each one ends, by default with [failure] or success. */
internal class FakeEmailAuthManager(private val failure: Exception? = null) : EmailAuthManager {
    val signIns = mutableListOf<Pair<String, String>>()
    val accountsCreated = mutableListOf<Pair<String, String>>()
    val passwordResets = mutableListOf<String>()
    var answer: suspend () -> Unit = { failure?.let { throw it } }

    val calls: Int get() = signIns.size + accountsCreated.size + passwordResets.size

    override suspend fun signIn(email: String, password: String) {
        signIns += email to password
        answer()
    }

    override suspend fun createAccount(email: String, password: String) {
        accountsCreated += email to password
        answer()
    }

    override suspend fun sendPasswordReset(email: String) {
        passwordResets += email
        answer()
    }
}

internal fun navigator(vararg keys: NavKey) = NavigatorImpl(NavBackStack<NavKey>().apply { addAll(keys) })

internal val NavigatorImpl.stack: List<NavKey> get() = navBackStack.toList()

internal fun user() = AuthUser(id = "id", displayName = null, email = null, photoUrl = null)
