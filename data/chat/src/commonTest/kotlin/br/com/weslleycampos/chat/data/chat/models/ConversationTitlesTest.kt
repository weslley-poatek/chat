package br.com.weslleycampos.chat.data.chat.models

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationTitlesTest {
    @Test
    fun aShortMessageIsItsOwnTitleTrimmed() {
        assertEquals("Add a leading option", ConversationTitles.from("  Add a leading option \n"))
    }

    @Test
    fun aLongMessageIsCutToItsFirstFortyCharacters() {
        val message = "Write a useDebounce hook in TS. Generic, cancel on unmount."

        assertEquals("Write a useDebounce hook in TS. Generic,", ConversationTitles.from(message))
        assertEquals(ConversationTitles.MAX_LENGTH, ConversationTitles.from(message).length)
    }

    @Test
    fun lineBreaksAndRunsOfSpacesBecomeOneSpaceBeforeTheCut() {
        assertEquals(
            "useEffect(() => { const id = setTimeout(",
            ConversationTitles.from("useEffect(() => {\n  const id = setTimeout(() => setDebounced(value), delay);"),
        )
    }

    @Test
    fun aCutThatEndsOnASpaceDropsIt() {
        assertEquals("word word word word word word word word", ConversationTitles.from("word ".repeat(8) + "tail"))
        assertEquals("a".repeat(39), ConversationTitles.from("a".repeat(39) + " bcd"))
    }

    @Test
    fun aCutNeverSplitsAnEmoji() {
        val title = ConversationTitles.from("a".repeat(39) + "🚀 launch")

        assertEquals("a".repeat(39), title)
    }
}
