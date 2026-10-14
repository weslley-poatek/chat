package br.com.weslleycampos.chat.data.auth.utils

import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthUser
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser

internal fun FirebaseUser.toAuthUser() = AuthUser(
    id = uid,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl?.toString(),
)

internal fun Exception.toAuthException(): AuthException = when (this) {
    is FirebaseNetworkException -> AuthException.NetworkError(message, this)
    is FirebaseAuthInvalidCredentialsException -> AuthException.InvalidCredentials(message, this)
    else -> AuthException.Unknown(message, this)
}
