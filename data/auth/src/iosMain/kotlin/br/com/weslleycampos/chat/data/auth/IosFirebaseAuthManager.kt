@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.toAuthException
import br.com.weslleycampos.chat.data.auth.utils.toAuthUser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.annotation.Single
import swiftPMImport.Chat.data.data.auth.FIRAuth
import swiftPMImport.Chat.data.data.auth.FIRGoogleAuthProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Single
internal class IosFirebaseAuthManager : AuthManager {
    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val auth = FIRAuth.auth()
        val handle = auth.addAuthStateDidChangeListener { _, user -> trySend(user?.toAuthUser()) }
        awaitClose { auth.removeAuthStateDidChangeListener(handle) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogle(idToken: String, accessToken: String?) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val credential = FIRGoogleAuthProvider.credentialWithIDToken(
                idToken = idToken,
                accessToken = requireNotNull(accessToken) { "Google sign-in on iOS needs an access token" },
            )
            FIRAuth.auth().signInWithCredential(credential) { _, error ->
                if (error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(error.toAuthException())
                }
            }
        }
    }

    override fun signOut() {
        FIRAuth.auth().signOut(null)
    }
}
