package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.awaitAuthTask
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIsNot
import kotlin.test.assertSame

class AwaitAuthTaskTest {
    @Test
    fun returnsTheResultOfASuccessfulTask() = runTest {
        assertEquals("done", awaitAuthTask { Tasks.forResult("done") })
    }

    @Test
    fun rethrowsAFailedTaskAsAnAuthException() = runTest {
        assertFailsWith<AuthException.WeakPassword> {
            awaitAuthTask<Unit> {
                Tasks.forException(FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "weak", "too short"))
            }
        }
        assertFailsWith<AuthException.InvalidEmail> {
            awaitAuthTask<Unit> {
                Tasks.forException(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_EMAIL", "badly formatted"))
            }
        }
    }

    @Test
    fun rethrowsAFailureWhileStartingAsAnAuthException() = runTest {
        assertFailsWith<AuthException.Unknown> {
            awaitAuthTask<Unit> { throw IllegalArgumentException("Given String is empty or null") }
        }
    }

    @Test
    fun rethrowsAnAuthExceptionThrownWhileStartingAsItIs() = runTest {
        val notSignedIn = AuthException.NotSignedIn("nobody", null)

        val thrown = assertFailsWith<AuthException.NotSignedIn> { awaitAuthTask<Unit> { throw notSignedIn } }

        assertSame(notSignedIn, thrown)
    }

    @Test
    fun letsCancellationThroughUntouched() = runTest {
        val cancellation = CancellationException("cancelled")

        val thrown = assertFailsWith<CancellationException> { awaitAuthTask<Unit> { throw cancellation } }
        val cancelledTask = assertFailsWith<CancellationException> { awaitAuthTask { Tasks.forCanceled<Unit>() } }

        assertSame(cancellation, thrown)
        assertIsNot<AuthException>(cancelledTask)
    }
}
