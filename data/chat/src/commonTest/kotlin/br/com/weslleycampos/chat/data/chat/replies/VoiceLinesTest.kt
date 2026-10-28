package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VoiceLinesTest {
    /** The prototype's reveal: the text split on spaces, one more word every 230 ms. */
    private fun words(text: String): Int = text.split(' ').size

    @Test
    fun theFirstExchangeIsThePrototypesUserTAndModelTToTheCharacter() {
        assertEquals(
            VoiceExchange(
                transcript = "So the debounce hook works, but I also want it to fire on the leading edge of a " +
                    "burst, not just the trailing one.",
                reply = "Add a leading flag. On the first change in a burst set the value immediately, then " +
                    "suppress until the window closes. I will put the timer in a ref so it survives renders.",
            ),
            VoiceLines.exchanges.first(),
        )
        // frames.md §2.6: 24 and 33 words.
        assertEquals(24, words(VoiceLines.exchanges.first().transcript))
        assertEquals(33, words(VoiceLines.exchanges.first().reply))
    }

    @Test
    fun aFewMoreExchangesFollowEachLineDifferentAndShortEnoughToRevealWithinItsPhase() {
        val added = VoiceLines.exchanges.drop(1)

        assertTrue(added.size >= 3, "a few more exchanges than the prototype's one")
        val listeningWords = 6500 / 230
        val speakingWords = 7000 / 230
        added.forEach { exchange ->
            assertTrue(words(exchange.transcript) <= listeningWords, "too long to hear: ${exchange.transcript}")
            assertTrue(words(exchange.reply) <= speakingWords, "too long to say: ${exchange.reply}")
            assertEquals(exchange.transcript.trim(), exchange.transcript)
            assertEquals(exchange.reply.trim(), exchange.reply)
            assertTrue("  " !in exchange.transcript && "  " !in exchange.reply)
        }
        assertEquals(VoiceLines.exchanges.size, VoiceLines.exchanges.map { it.transcript }.toSet().size)
        assertEquals(VoiceLines.exchanges.size, VoiceLines.exchanges.map { it.reply }.toSet().size)
    }

    @Test
    fun aConversationsExchangesRotateThroughTheLinesInOrder() {
        val size = VoiceLines.exchanges.size

        (0..2 * size).forEach { exchangesSoFar ->
            assertSame(VoiceLines.exchanges[exchangesSoFar % size], VoiceLines.next(exchangesSoFar))
        }
    }
}
