package br.com.weslleycampos.chat.data.settings.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every key here is an obvious fake. */
class KeyMaskTest {
    @Test
    fun aKeyOf22CharactersOrMoreShowsItsFirst7AndLast4AroundAnEllipsisAsThePrototypeDoes() {
        assertEquals("sk-ant-…0000", KeyMask.of("sk-ant-test-0000000000000000000000000000"))
        assertEquals("sk-test…1234", KeyMask.of("sk-test-000000000001234"))
        assertEquals("abcdefg…wxyz", KeyMask.of("abcdefg-0123456789wxyz"))
        assertEquals(22, "abcdefg-0123456789wxyz".length)
    }

    @Test
    fun aShorterKeyOf8To21CharactersShowsOnlyAnEllipsisAndItsLast4() {
        assertEquals("…0000", KeyMask.of("sk-test-0000000000"))
        assertEquals("…wxyz", KeyMask.of("abcdefg-012345678wxyz"))
        assertEquals("…5678", KeyMask.of("fake5678"))
    }

    @Test
    fun aKeyOfFewerThan8CharactersShowsOnlyAnEllipsis() {
        for (key in listOf("fake567", "abc", "x")) {
            assertEquals("…", KeyMask.of(key), key)
        }
    }

    @Test
    fun theMaskNeverShowsMoreThanHalfOfAKey() {
        for (length in 1..64) {
            val key = (1..length).joinToString("") { ('a' + it % 26).toString() }
            val shown = KeyMask.of(key).replace(KeyMask.ELLIPSIS, "").length

            assertTrue(shown * 2 <= length, "a key of $length characters shows $shown")
        }
    }
}
