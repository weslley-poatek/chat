@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.toAuthException
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeEmailAlreadyInUse
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeInvalidCredential
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeInvalidEmail
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeNetworkError
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeOperationNotAllowed
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeTooManyRequests
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeUserDisabled
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeUserNotFound
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeWeakPassword
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeWrongPassword
import kotlin.test.Test
import kotlin.test.assertIs

class NSErrorAuthExceptionMappingTest {
    private fun authError(code: Long) = NSError.errorWithDomain("FIRAuthErrorDomain", code, null)

    @Test
    fun mapsFirebaseAuthErrorCodesToAuthExceptions() {
        assertIs<AuthException.NetworkError>(authError(FIRAuthErrorCodeNetworkError).toAuthException())
        assertIs<AuthException.InvalidCredentials>(authError(FIRAuthErrorCodeInvalidCredential).toAuthException())
        for (code in listOf(FIRAuthErrorCodeUserDisabled, FIRAuthErrorCodeOperationNotAllowed, 0L)) {
            assertIs<AuthException.Unknown>(authError(code).toAuthException())
        }
    }

    @Test
    fun mapsEmailPasswordErrorCodesToTheirOwnAuthExceptions() {
        assertIs<AuthException.InvalidEmail>(authError(FIRAuthErrorCodeInvalidEmail).toAuthException())
        assertIs<AuthException.InvalidCredentials>(authError(FIRAuthErrorCodeWrongPassword).toAuthException())
        assertIs<AuthException.UserNotFound>(authError(FIRAuthErrorCodeUserNotFound).toAuthException())
        assertIs<AuthException.EmailAlreadyInUse>(authError(FIRAuthErrorCodeEmailAlreadyInUse).toAuthException())
        assertIs<AuthException.WeakPassword>(authError(FIRAuthErrorCodeWeakPassword).toAuthException())
        assertIs<AuthException.TooManyRequests>(authError(FIRAuthErrorCodeTooManyRequests).toAuthException())
    }
}
