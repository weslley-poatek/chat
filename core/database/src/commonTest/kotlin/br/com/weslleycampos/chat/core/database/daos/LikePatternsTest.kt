package br.com.weslleycampos.chat.core.database.daos

import kotlin.test.Test
import kotlin.test.assertEquals

class LikePatternsTest {
    @Test
    fun aContainsPatternWrapsTheTextInWildcards() {
        assertEquals("%debounce%", containsPattern("debounce"))
        assertEquals("%%", containsPattern(""))
    }

    @Test
    fun percentUnderscoreAndBackslashAreEscapedWithABackslash() {
        assertEquals("""%50\% off\_now \\ ok%""", containsPattern("""50% off_now \ ok"""))
    }
}
