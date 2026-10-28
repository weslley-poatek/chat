@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.data.auth.utils

import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthUser
import br.com.weslleycampos.chat.data.auth.SignInMethod
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeEmailAlreadyInUse
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeInvalidCredential
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeInvalidEmail
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeInvalidUserToken
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeNetworkError
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeRequiresRecentLogin
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeTooManyRequests
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeUserNotFound
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeUserTokenExpired
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeWeakPassword
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeWrongPassword
import swiftPMImport.Chat.data.data.auth.FIRUser
import swiftPMImport.Chat.data.data.auth.FIRUserInfoProtocol

internal fun FIRUser.toAuthUser() = AuthUser(
    id = uid,
    displayName = displayName,
    email = email,
    photoUrl = photoURL?.absoluteString,
)

/** How this user can sign in, from the providers Firebase lists for them. */
internal fun FIRUser.signInMethods(): List<SignInMethod> =
    SignInMethod.of(providerData.mapNotNull { (it as? FIRUserInfoProtocol)?.providerID })

internal fun NSError.toAuthException(): AuthException = when (code) {
    FIRAuthErrorCodeNetworkError -> AuthException.NetworkError(localizedDescription, null)
    FIRAuthErrorCodeInvalidCredential,
    FIRAuthErrorCodeWrongPassword,
    -> AuthException.InvalidCredentials(localizedDescription, null)
    FIRAuthErrorCodeInvalidEmail -> AuthException.InvalidEmail(localizedDescription, null)
    FIRAuthErrorCodeUserNotFound -> AuthException.UserNotFound(localizedDescription, null)
    FIRAuthErrorCodeEmailAlreadyInUse -> AuthException.EmailAlreadyInUse(localizedDescription, null)
    FIRAuthErrorCodeWeakPassword -> AuthException.WeakPassword(localizedDescription, null)
    FIRAuthErrorCodeTooManyRequests -> AuthException.TooManyRequests(localizedDescription, null)
    // An expired or revoked session needs the same fresh sign-in as a stale one before a delete.
    FIRAuthErrorCodeRequiresRecentLogin,
    FIRAuthErrorCodeUserTokenExpired,
    FIRAuthErrorCodeInvalidUserToken,
    -> AuthException.RequiresRecentLogin(localizedDescription, null)
    else -> AuthException.Unknown(localizedDescription, null)
}
