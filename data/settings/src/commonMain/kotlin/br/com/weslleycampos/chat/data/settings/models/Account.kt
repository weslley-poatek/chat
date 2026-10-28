package br.com.weslleycampos.chat.data.settings.models

import br.com.weslleycampos.chat.data.auth.AuthUser
import br.com.weslleycampos.chat.data.auth.SignInMethod

/**
 * The signed-in account, as Settings' "account" group shows it.
 *
 * @property user The profile row's name and email ([AuthUser.displayName] is often `null` for an
 *   email + password account).
 * @property signInMethods The "Sign-in methods" summary: Google, email and password, or both, in
 *   [SignInMethod] order.
 */
data class Account(val user: AuthUser, val signInMethods: List<SignInMethod>)
