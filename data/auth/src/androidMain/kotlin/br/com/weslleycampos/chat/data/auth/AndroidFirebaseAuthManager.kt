package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.toAuthException
import br.com.weslleycampos.chat.data.auth.utils.toAuthUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import org.koin.core.annotation.Single

@Single
internal class AndroidFirebaseAuthManager : AuthManager {
    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAuthUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?) {
        try {
            FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, accessToken))
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toAuthException()
        }
    }

    override fun signOut() = FirebaseAuth.getInstance().signOut()
}
