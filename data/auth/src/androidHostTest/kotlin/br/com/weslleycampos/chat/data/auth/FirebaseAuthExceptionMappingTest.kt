package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.toAuthException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

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
        assertIs<AuthException.Unknown>(
            FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "provider disabled").toAuthException()
        )
        assertIs<AuthException.Unknown>(IllegalStateException("boom").toAuthException())
    }

    @Test
    fun mapsEmailPasswordFailuresToTheirOwnAuthExceptions() {
        assertIs<AuthException.InvalidEmail>(
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "badly formatted").toAuthException()
        )
        assertIs<AuthException.InvalidCredentials>(
            FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "wrong").toAuthException()
        )
        assertIs<AuthException.UserNotFound>(
            FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no user").toAuthException()
        )
        assertIs<AuthException.EmailAlreadyInUse>(
            FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "taken").toAuthException()
        )
        assertIs<AuthException.WeakPassword>(
            FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak", "too short").toAuthException()
        )
        assertIs<AuthException.TooManyRequests>(FirebaseTooManyRequestsException("throttled").toAuthException())
    }

    @Test
    fun mapsAFreshSignInThatDeletingTheAccountNeedsToRequiresRecentLogin() {
        assertIs<AuthException.RequiresRecentLogin>(
            FirebaseAuthRecentLoginRequiredException("ERROR_REQUIRES_RECENT_LOGIN", "sign in again").toAuthException()
        )
        assertIs<AuthException.RequiresRecentLogin>(
            FirebaseAuthInvalidUserException("ERROR_USER_TOKEN_EXPIRED", "expired").toAuthException()
        )
        assertIs<AuthException.RequiresRecentLogin>(
            FirebaseAuthInvalidUserException("ERROR_INVALID_USER_TOKEN", "revoked").toAuthException()
        )
        assertIs<AuthException.UserNotFound>(
            FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "already deleted").toAuthException()
        )
    }

    @Test
    fun passesAnAuthExceptionThroughAsItIs() {
        val notSignedIn = AuthException.NotSignedIn("nobody", null)

        assertSame(notSignedIn, notSignedIn.toAuthException())
    }

    @Test
    fun keepsTheFirebaseExceptionAsTheCause() {
        val failure = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "taken")

        assertSame(failure, failure.toAuthException().cause)
    }
}
