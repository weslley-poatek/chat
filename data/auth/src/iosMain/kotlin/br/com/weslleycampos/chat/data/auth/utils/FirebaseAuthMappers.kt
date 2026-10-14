@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.data.auth.utils

import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthUser
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeInvalidCredential
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeNetworkError
import swiftPMImport.Chat.data.data.auth.FIRUser

internal fun FIRUser.toAuthUser() = AuthUser(
    id = uid,
    displayName = displayName,
    email = email,
    photoUrl = photoURL?.absoluteString,
)

internal fun NSError.toAuthException(): AuthException = when (code) {
    FIRAuthErrorCodeNetworkError -> AuthException.NetworkError(localizedDescription, null)
    FIRAuthErrorCodeInvalidCredential -> AuthException.InvalidCredentials(localizedDescription, null)
    else -> AuthException.Unknown(localizedDescription, null)
}
