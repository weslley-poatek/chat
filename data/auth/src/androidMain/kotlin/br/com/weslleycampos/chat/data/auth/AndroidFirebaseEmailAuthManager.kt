package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.awaitAuthTask
import com.google.firebase.auth.FirebaseAuth
import org.koin.core.annotation.Single

@Single
internal class AndroidFirebaseEmailAuthManager : EmailAuthManager {
    override suspend fun signIn(email: String, password: String) {
        awaitAuthTask { FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password) }
    }

    override suspend fun createAccount(email: String, password: String) {
        awaitAuthTask { FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password) }
    }

    override suspend fun sendPasswordReset(email: String) {
        awaitAuthTask { FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()) }
    }
}
