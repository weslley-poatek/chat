package br.com.weslleycampos.chat.data.auth

import br.com.weslleycampos.chat.data.auth.utils.requireSignedIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SignedInUserTest {
    @Test
    fun aCallOnTheSignedInUserGetsThatUser() {
        assertEquals("user", requireSignedIn("user"))
    }

    @Test
    fun aCallOnTheSignedInUserWhileNobodyIsSignedInThrowsNotSignedIn() {
        assertFailsWith<AuthException.NotSignedIn> { requireSignedIn<String>(null) }
    }
}
