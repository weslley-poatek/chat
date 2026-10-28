package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.awaitAuthTask
import br.com.weslleycampos.chat.data.auth.utils.requireSignedIn
import br.com.weslleycampos.chat.data.auth.utils.signInMethods
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.annotation.Single

@Single
internal class AndroidFirebaseAccountManager : AccountManager {
    override val signInMethods: Flow<List<SignInMethod>> = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.signInMethods().orEmpty()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun deleteAccount() {
        awaitAuthTask { requireSignedIn(FirebaseAuth.getInstance().currentUser).delete() }
    }
}
