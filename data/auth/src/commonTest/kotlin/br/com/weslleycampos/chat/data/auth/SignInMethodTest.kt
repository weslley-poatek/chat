package br.com.weslleycampos.chat.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignInMethodTest {
    @Test
    fun mapsFirebasesProviderIdsToGoogleThenEmailAndPasswordWhateverTheirOrder() {
        assertEquals(
            listOf(SignInMethod.GOOGLE, SignInMethod.EMAIL_PASSWORD),
            SignInMethod.of(listOf("firebase", "password", "google.com")),
        )
        assertEquals(listOf(SignInMethod.GOOGLE), SignInMethod.of(listOf("firebase", "google.com")))
        assertEquals(listOf(SignInMethod.EMAIL_PASSWORD), SignInMethod.of(listOf("password", "firebase")))
    }

    @Test
    fun listsEachMethodOnceAndLeavesProvidersThisAppNeverSignsInWithOut() {
        assertEquals(
            listOf(SignInMethod.GOOGLE, SignInMethod.EMAIL_PASSWORD),
            SignInMethod.of(listOf("google.com", "password", "google.com", "github.com", "apple.com", "phone")),
        )
        assertTrue(SignInMethod.of(listOf("firebase", "github.com")).isEmpty())
        assertTrue(SignInMethod.of(emptyList()).isEmpty())
    }
}
