package br.com.weslleycampos.chat.data.auth.utils

import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthUser
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser

internal fun FirebaseUser.toAuthUser() = AuthUser(
    id = uid,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl?.toString(),
)

internal fun Exception.toAuthException(): AuthException = when (this) {
    is FirebaseNetworkException -> AuthException.NetworkError(message, this)
    is FirebaseTooManyRequestsException -> AuthException.TooManyRequests(message, this)
    is FirebaseAuthWeakPasswordException -> AuthException.WeakPassword(message, this)
    is FirebaseAuthException -> byErrorCode()
    else -> AuthException.Unknown(message, this)
}

/**
 * By error code, because one Firebase class covers several causes: a malformed email and a wrong
 * password are both a [FirebaseAuthInvalidCredentialsException].
 */
private fun FirebaseAuthException.byErrorCode(): AuthException = when (errorCode) {
    "ERROR_INVALID_EMAIL" -> AuthException.InvalidEmail(message, this)
    "ERROR_USER_NOT_FOUND" -> AuthException.UserNotFound(message, this)
    "ERROR_EMAIL_ALREADY_IN_USE" -> AuthException.EmailAlreadyInUse(message, this)
    "ERROR_WEAK_PASSWORD" -> AuthException.WeakPassword(message, this)
    else -> when (this) {
        is FirebaseAuthInvalidCredentialsException -> AuthException.InvalidCredentials(message, this)
        else -> AuthException.Unknown(message, this)
    }
}
