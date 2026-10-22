package br.com.weslleycampos.chat.data.auth

/**
 * Why Firebase Auth refused a call on [AuthManager] or [EmailAuthManager]. Each case is one message
 * the sign-in screen can show; anything it has no message for is [Unknown].
 */
sealed class AuthException(message: String?, cause: Throwable?) : Exception(message, cause) {
    /** The email is not an address Firebase accepts. */
    class InvalidEmail(message: String?, cause: Throwable?) : AuthException(message, cause)

    /**
     * A wrong password, or a credential Firebase rejected. With email enumeration protection on, the
     * Firebase default, a sign-in to an email with no account also ends here instead of [UserNotFound].
     */
    class InvalidCredentials(message: String?, cause: Throwable?) : AuthException(message, cause)

    /** No account has this email. Only reported while email enumeration protection is off. */
    class UserNotFound(message: String?, cause: Throwable?) : AuthException(message, cause)

    /** Creating an account with an email that already has one. */
    class EmailAlreadyInUse(message: String?, cause: Throwable?) : AuthException(message, cause)

    /** Creating an account with a password Firebase's password policy rejects. */
    class WeakPassword(message: String?, cause: Throwable?) : AuthException(message, cause)

    /** Firebase blocked this device for a while after too many attempts. */
    class TooManyRequests(message: String?, cause: Throwable?) : AuthException(message, cause)

    class NetworkError(message: String?, cause: Throwable?) : AuthException(message, cause)

    class Unknown(message: String?, cause: Throwable?) : AuthException(message, cause)
}
