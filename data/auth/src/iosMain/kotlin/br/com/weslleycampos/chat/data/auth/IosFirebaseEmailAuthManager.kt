@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.awaitAuthCompletion
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.annotation.Single
import swiftPMImport.Chat.data.data.auth.FIRAuth

@Single
internal class IosFirebaseEmailAuthManager : EmailAuthManager {
    override suspend fun signIn(email: String, password: String) {
        awaitAuthCompletion { onComplete ->
            FIRAuth.auth().signInWithEmail(email = email.trim(), password = password) { _, error ->
                onComplete(error)
            }
        }
    }

    override suspend fun createAccount(email: String, password: String) {
        awaitAuthCompletion { onComplete ->
            FIRAuth.auth().createUserWithEmail(email = email.trim(), password = password) { _, error ->
                onComplete(error)
            }
        }
    }

    override suspend fun sendPasswordReset(email: String) {
        awaitAuthCompletion { onComplete ->
            FIRAuth.auth().sendPasswordResetWithEmail(email = email.trim()) { error -> onComplete(error) }
        }
    }
}
