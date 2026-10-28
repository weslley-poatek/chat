package br.com.weslleycampos.chat.data.settings.fakes

import br.com.weslleycampos.chat.data.auth.AccountManager
import br.com.weslleycampos.chat.data.auth.AuthManager
import br.com.weslleycampos.chat.data.auth.AuthUser
import br.com.weslleycampos.chat.data.auth.SignInMethod
import br.com.weslleycampos.chat.data.chat.ChatDataEraser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal val ANA = AuthUser(id = "uid-1", displayName = "Ana Ribeiro", email = "ana@example.dev", photoUrl = null)

/** Firebase's signed-in user in memory; [signOut] signs out, as Firebase does. */
internal class FakeAuthManager(user: AuthUser? = ANA) : AuthManager {
    val user = MutableStateFlow(user)
    var signOuts = 0
        private set

    override val currentUser: Flow<AuthUser?> = this.user

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?) = Unit

    override fun signOut() {
        signOuts++
        user.value = null
    }
}

/**
 * The account's sign-in methods and its delete in memory. A delete appends "delete" to [events],
 * then waits for [answer] when one is set, then throws [failure] when one is set, or else deletes:
 * Firebase signs the user out of [auth].
 */
internal class FakeAccountManager(
    private val auth: FakeAuthManager,
    private val events: MutableList<String>,
    methods: List<SignInMethod> = listOf(SignInMethod.GOOGLE),
) : AccountManager {
    val methods = MutableStateFlow(methods)
    var failure: Throwable? = null
    var answer: CompletableDeferred<Unit>? = null

    override val signInMethods: Flow<List<SignInMethod>> = this.methods

    override suspend fun deleteAccount() {
        events += "delete"
        answer?.await()
        failure?.let { throw it }
        events += "deleted"
        auth.user.value = null
        methods.value = emptyList()
    }
}

/** Appends "erase chat" to [events] on every erase. */
internal class FakeChatDataEraser(private val events: MutableList<String>) : ChatDataEraser {
    override suspend fun eraseAll() {
        events += "erase chat"
    }
}
