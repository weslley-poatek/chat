package br.com.weslleycampos.chat.data.auth.utils

import br.com.weslleycampos.chat.data.auth.AuthException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Starts a Firebase Auth call that reports back through a completion block, and waits for it. An
 * error is rethrown as an [AuthException]. Cancelling the caller cancels the wait and throws its
 * `CancellationException`; Firebase's late completion is then ignored.
 */
internal suspend fun awaitAuthCompletion(start: (onComplete: (NSError?) -> Unit) -> Unit) {
    suspendCancellableCoroutine<Unit> { continuation ->
        start { error ->
            if (error == null) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(error.toAuthException())
            }
        }
    }
}
