package br.com.weslleycampos.chat.data.auth

import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Provided

/**
 * The signed-in account on Firebase Auth, next to [AuthManager]'s user and sign-out: how it signs in,
 * and deleting it. Settings' "account" group reads it.
 *
 * `@Provided` for the same reason as [AuthManager]: each platform source set binds its own
 * implementation.
 */
@Provided
interface AccountManager {
    /**
     * How the signed-in user can sign in, from Firebase's provider data: Google, email and
     * password, or both, in [SignInMethod] order. Empty while nobody is signed in. Emits the
     * current value as soon as it is collected and again whenever the user signs in or out, never
     * the same list twice in a row.
     */
    val signInMethods: Flow<List<SignInMethod>>

    /**
     * Deletes the signed-in user's Firebase account. When it returns, the account is gone and
     * Firebase has signed the user out, so [AuthManager.currentUser] emits `null`. Deletes nothing
     * on this device: conversations, choices and keys are the caller's to wipe.
     *
     * Throws an [AuthException] when nothing was deleted: [AuthException.NotSignedIn] while nobody
     * is signed in, [AuthException.RequiresRecentLogin] when Firebase wants a fresh sign-in first,
     * [AuthException.NetworkError], [AuthException.TooManyRequests], [AuthException.UserNotFound]
     * for an account already deleted elsewhere, and [AuthException.Unknown] for anything else.
     * Cancellation passes through untouched; Firebase may still finish a delete it had started.
     */
    suspend fun deleteAccount()
}
