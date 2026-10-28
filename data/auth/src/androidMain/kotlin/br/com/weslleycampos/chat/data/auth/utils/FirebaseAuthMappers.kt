package br.com.weslleycampos.chat.data.auth.utils

import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthUser
import br.com.weslleycampos.chat.data.auth.SignInMethod
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser

internal fun FirebaseUser.toAuthUser() = AuthUser(
    id = uid,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl?.toString(),
)

/** How this user can sign in, from the providers Firebase lists for them. */
internal fun FirebaseUser.signInMethods(): List<SignInMethod> = SignInMethod.of(providerData.map { it.providerId })

internal fun Exception.toAuthException(): AuthException = when (this) {
    // Thrown by this module while starting a call, such as `requireSignedIn`: already mapped.
    is AuthException -> this
    is FirebaseNetworkException -> AuthException.NetworkError(message, this)
    is FirebaseTooManyRequestsException -> AuthException.TooManyRequests(message, this)
    is FirebaseAuthWeakPasswordException -> AuthException.WeakPassword(message, this)
    is FirebaseAuthRecentLoginRequiredException -> AuthException.RequiresRecentLogin(message, this)
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
    // An expired or revoked session needs the same fresh sign-in as a stale one before a delete.
    "ERROR_REQUIRES_RECENT_LOGIN",
    "ERROR_USER_TOKEN_EXPIRED",
    "ERROR_INVALID_USER_TOKEN",
    -> AuthException.RequiresRecentLogin(message, this)
    else -> when (this) {
        is FirebaseAuthInvalidCredentialsException -> AuthException.InvalidCredentials(message, this)
        else -> AuthException.Unknown(message, this)
    }
}
