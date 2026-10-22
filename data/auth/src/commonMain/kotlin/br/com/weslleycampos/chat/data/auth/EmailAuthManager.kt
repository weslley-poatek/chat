package br.com.weslleycampos.chat.data.auth

import org.koin.core.annotation.Provided

/**
 * Email + password accounts on Firebase Auth, next to the Google sign-in in [AuthManager]. A
 * successful [signIn] or [createAccount] signs the user in, so [AuthManager.currentUser] emits them,
 * and [AuthManager.signOut] signs them out again.
 *
 * Every call throws an [AuthException] when Firebase refuses it and lets cancellation through
 * untouched. The email is trimmed before it is sent; the password is sent exactly as typed. Check
 * both with [EmailAuthRules] first.
 *
 * `@Provided` for the same reason as [AuthManager]: each platform source set binds its own
 * implementation.
 */
@Provided
interface EmailAuthManager {
    /** Signs in to an existing account. */
    suspend fun signIn(email: String, password: String)

    /** Creates an account and signs in to it. */
    suspend fun createAccount(email: String, password: String)

    /**
     * Emails a link to choose a new password. Signs nobody in. With email enumeration protection
     * on, the Firebase default, it also succeeds for an email with no account and sends nothing.
     */
    suspend fun sendPasswordReset(email: String)
}
