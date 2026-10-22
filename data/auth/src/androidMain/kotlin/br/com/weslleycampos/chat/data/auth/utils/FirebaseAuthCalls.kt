package br.com.weslleycampos.chat.data.auth.utils

import br.com.weslleycampos.chat.data.auth.AuthException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Starts a Firebase Auth task and waits for it. Any failure, including one thrown while starting it,
 * is rethrown as an [AuthException]; cancellation passes through untouched.
 */
internal suspend fun <T> awaitAuthTask(start: () -> Task<T>): T =
    try {
        start().await()
    } catch (e: Exception) {
        when (e) {
            is CancellationException -> throw e
            else -> throw e.toAuthException()
        }
    }
