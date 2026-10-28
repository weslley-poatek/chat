package br.com.weslleycampos.chat.core.navigation.entries.auth

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Sign-in: Google, or email + password with account creation and the password reset as modes of
 * the same screen. The session gate and Settings' "Sign out" open it on sign in; deleting the
 * account opens it on account creation, as the prototype does.
 *
 * @property signUp Whether the screen opens on "Create account" instead of "Sign in". Only the
 *   first mode: the screen's links switch modes as usual.
 */
@Serializable
data class SignInEntry(val signUp: Boolean = false) : NavKey
