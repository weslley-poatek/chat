@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.data.auth.utils

import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthUser
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeEmailAlreadyInUse
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeInvalidCredential
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeInvalidEmail
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeNetworkError
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeTooManyRequests
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeUserNotFound
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeWeakPassword
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeWrongPassword
import swiftPMImport.Chat.data.data.auth.FIRUser

internal fun FIRUser.toAuthUser() = AuthUser(
    id = uid,
    displayName = displayName,
    email = email,
    photoUrl = photoURL?.absoluteString,
)

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
    else -> AuthException.Unknown(localizedDescription, null)
}
