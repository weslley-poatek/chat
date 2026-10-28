@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.awaitAuthCompletion
import br.com.weslleycampos.chat.data.auth.utils.requireSignedIn
import br.com.weslleycampos.chat.data.auth.utils.signInMethods
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.annotation.Single
import swiftPMImport.Chat.data.data.auth.FIRAuth

@Single
internal class IosFirebaseAccountManager : AccountManager {
    override val signInMethods: Flow<List<SignInMethod>> = callbackFlow {
        val auth = FIRAuth.auth()
        val handle = auth.addAuthStateDidChangeListener { _, user -> trySend(user?.signInMethods().orEmpty()) }
        awaitClose { auth.removeAuthStateDidChangeListener(handle) }
    }.distinctUntilChanged()

    override suspend fun deleteAccount() {
        val user = requireSignedIn(FIRAuth.auth().currentUser)
        awaitAuthCompletion { onComplete -> user.deleteWithCompletion { error -> onComplete(error) } }
    }
}
