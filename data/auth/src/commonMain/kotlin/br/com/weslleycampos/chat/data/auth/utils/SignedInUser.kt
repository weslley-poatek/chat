package br.com.weslleycampos.chat.data.auth.utils

import br.com.weslleycampos.chat.data.auth.AuthException

/** [user], for a call on the signed-in user, or [AuthException.NotSignedIn] while there is none. */
internal fun <T : Any> requireSignedIn(user: T?): T =
    user ?: throw AuthException.NotSignedIn("No user is signed in", null)
