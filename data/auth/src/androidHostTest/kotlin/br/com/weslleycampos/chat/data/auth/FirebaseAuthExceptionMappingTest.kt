package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.toAuthException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlin.test.Test
import kotlin.test.assertIs

class FirebaseAuthExceptionMappingTest {
    @Test
    fun mapsFirebaseFailuresToAuthExceptions() {
        assertIs<AuthException.NetworkError>(FirebaseNetworkException("offline").toAuthException())
        assertIs<AuthException.InvalidCredentials>(
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "rejected").toAuthException()
        )
        assertIs<AuthException.Unknown>(
            FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled").toAuthException()
        )
        assertIs<AuthException.Unknown>(
            FirebaseAuthUserCollisionException(
                "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
                "exists",
            ).toAuthException()
        )
        assertIs<AuthException.Unknown>(FirebaseTooManyRequestsException("throttled").toAuthException())
        assertIs<AuthException.Unknown>(IllegalStateException("boom").toAuthException())
    }
}
