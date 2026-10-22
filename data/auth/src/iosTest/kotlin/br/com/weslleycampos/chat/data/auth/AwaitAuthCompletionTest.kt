@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.awaitAuthCompletion
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSError
import swiftPMImport.Chat.data.data.auth.FIRAuthErrorCodeEmailAlreadyInUse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AwaitAuthCompletionTest {
    @Test
    fun returnsWhenTheCompletionReportsNoError() = runTest {
        var started = 0

        awaitAuthCompletion { onComplete ->
            started++
            onComplete(null)
        }

        assertEquals(1, started)
    }

    @Test
    fun rethrowsTheCompletionErrorAsAnAuthException() = runTest {
        val error = NSError.errorWithDomain("FIRAuthErrorDomain", FIRAuthErrorCodeEmailAlreadyInUse, null)

        assertFailsWith<AuthException.EmailAlreadyInUse> { awaitAuthCompletion { onComplete -> onComplete(error) } }
    }

    @Test
    fun cancellingTheCallerStopsTheWaitAndIgnoresALateCompletion() = runTest {
        val completion = CompletableDeferred<(NSError?) -> Unit>()
        val call = launch(start = CoroutineStart.UNDISPATCHED) {
            awaitAuthCompletion { onComplete -> completion.complete(onComplete) }
        }

        call.cancel()
        call.join()
        completion.await().invoke(null)

        assertTrue(call.isCancelled)
    }
}
