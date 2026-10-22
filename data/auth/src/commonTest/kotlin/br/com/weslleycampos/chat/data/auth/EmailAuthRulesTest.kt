package br.com.weslleycampos.chat.data.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailAuthRulesTest {
    @Test
    fun acceptsAnEmailWithSomethingAroundTheAtAndADotAfterIt() {
        for (email in listOf("you@example.dev", "a@b.c", "first.last+tag@mail.example.co.uk", "  you@example.dev ")) {
            assertTrue(EmailAuthRules.isValidEmail(email), email)
        }
    }

    @Test
    fun rejectsAnythingElseAsAnEmail() {
        for (email in listOf("", "   ", "you", "you@", "@example.dev", "you@example", "you@example.", "you@.dev")) {
            assertFalse(EmailAuthRules.isValidEmail(email), email)
        }
    }

    @Test
    fun acceptsAPasswordOfEightOrMoreCharacters() {
        assertEquals(8, EmailAuthRules.MIN_PASSWORD_LENGTH)
        for (password in listOf("12345678", "correct horse battery", "        ")) {
            assertTrue(EmailAuthRules.isValidPassword(password), password)
        }
        for (password in listOf("", "1234567", " 123456")) {
            assertFalse(EmailAuthRules.isValidPassword(password), password)
        }
    }
}
