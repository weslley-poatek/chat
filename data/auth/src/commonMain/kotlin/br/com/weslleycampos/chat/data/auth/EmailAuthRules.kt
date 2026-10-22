package br.com.weslleycampos.chat.data.auth

/**
 * The checks the email + password form runs before it calls [EmailAuthManager], the same ones the
 * design prototype runs. Firebase still checks on its side, and its refusals arrive as
 * [AuthException].
 */
object EmailAuthRules {
    /** The shortest password an account is created with: the "8+ chars" sign up shows. */
    const val MIN_PASSWORD_LENGTH = 8

    private val emailShape = Regex(".+@.+\\..+")

    /**
     * Something, an `@`, something, a dot and something, after trimming: the prototype's
     * `/.+@.+\..+/`. Shape only; whether the address exists is Firebase's to say.
     */
    fun isValidEmail(email: String): Boolean = emailShape.containsMatchIn(email.trim())

    /** At least [MIN_PASSWORD_LENGTH] characters, spaces included, since passwords are not trimmed. */
    fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH
}
