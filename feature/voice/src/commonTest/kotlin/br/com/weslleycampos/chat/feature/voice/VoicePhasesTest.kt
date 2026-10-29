package br.com.weslleycampos.chat.feature.voice

import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/** The prototype's `USER_T` and `MODEL_T` (lines 608–609): 24 and 33 words. */
private val Design = VoiceExchange(
    transcript = "So the debounce hook works, but I also want it to fire on the leading edge of a burst, " +
        "not just the trailing one.",
    reply = "Add a leading flag. On the first change in a burst set the value immediately, then suppress " +
        "until the window closes. I will put the timer in a ref so it survives renders.",
)

class VoicePhasesTest {
    @Test
    fun listeningEndsAfterTwoSecondsOfSilenceAndListensAgainHalfASecondAfterHearingNothing() {
        assertEquals(2000.milliseconds, SilenceAfterSpeech)
        assertEquals(500.milliseconds, ListenAgainPause)
    }

    @Test
    fun withNoVoiceSpeakingLastsAWordEvery230MsThenASecondOnTheWholeReply() {
        assertEquals(1230.milliseconds, speakingDuration("Sure."))
        assertEquals(1920.milliseconds, speakingDuration("add a leading flag"))
        // The design's 33-word reply: 7590 ms of words, its last one in place for a second.
        assertEquals(8590.milliseconds, speakingDuration(Design.reply))
        assertEquals(WordInterval * 33 + 1000.milliseconds, speakingDuration(Design.reply))
    }

    @Test
    fun aLongerReplySpeaksLongerOneWordIntervalAWord() {
        val short = "use fake timers"
        val longer = "$short and advance them"

        assertEquals(WordInterval * 3, speakingDuration(longer) - speakingDuration(short))
    }

    @Test
    fun speakingLastsUntilItsLastWordHasShownForASecond() {
        val words = Design.reply.split(' ').size
        val lastWordShows = WordInterval * words

        assertEquals(
            VoiceCaption(shown = Design.reply, rest = ""),
            voiceCaption(phase = ChatVoicePhase.Speaking, exchange = Design, words = words),
        )
        assertEquals(1000.milliseconds, speakingDuration(Design.reply) - lastWordShows)
    }

    @Test
    fun aWordShowsEvery230Ms() {
        assertEquals(230.milliseconds, WordInterval)
    }

    @Test
    fun listeningRevealsTheTranscriptWordByWordWithTheRestOfTheSentenceToCome() {
        assertEquals(
            VoiceCaption(shown = "", rest = Design.transcript),
            voiceCaption(phase = ChatVoicePhase.Listening, exchange = Design, words = 0),
        )
        assertEquals(
            VoiceCaption(
                shown = "So the debounce",
                rest = " hook works, but I also want it to fire on the leading edge of a burst, " +
                    "not just the trailing one.",
            ),
            voiceCaption(phase = ChatVoicePhase.Listening, exchange = Design, words = 3),
        )
        assertEquals(
            VoiceCaption(shown = Design.transcript, rest = ""),
            voiceCaption(phase = ChatVoicePhase.Listening, exchange = Design, words = 24),
        )
        // 28 words by the end of listening: more than the line has.
        assertEquals(
            VoiceCaption(shown = Design.transcript, rest = ""),
            voiceCaption(phase = ChatVoicePhase.Listening, exchange = Design, words = 28),
        )
    }

    @Test
    fun thinkingShowsTheWholeTranscriptWhateverTheWordCount() {
        for (words in listOf(0, 5, 40)) {
            assertEquals(
                VoiceCaption(shown = Design.transcript, rest = ""),
                voiceCaption(phase = ChatVoicePhase.Thinking, exchange = Design, words = words),
            )
        }
    }

    @Test
    fun speakingRevealsTheReplyWordByWordWithTheRestOfTheSentenceToCome() {
        assertEquals(
            VoiceCaption(
                shown = "Add a",
                rest = " leading flag. On the first change in a burst set the value immediately, " +
                    "then suppress until the window closes. I will put the timer in a ref so it survives renders.",
            ),
            voiceCaption(phase = ChatVoicePhase.Speaking, exchange = Design, words = 2),
        )
        // 7000 ms in at 230 ms a word, the prototype's whole speaking phase: 30 of the reply's 33 words.
        assertEquals(
            VoiceCaption(
                shown = "Add a leading flag. On the first change in a burst set the value immediately, then suppress " +
                    "until the window closes. I will put the timer in a ref so",
                rest = " it survives renders.",
            ),
            voiceCaption(phase = ChatVoicePhase.Speaking, exchange = Design, words = 30),
        )
    }

    @Test
    fun beforeTheSessionIsReadThereIsNoCaption() {
        for (phase in ChatVoicePhase.entries) {
            assertEquals(VoiceCaption.Empty, voiceCaption(phase = phase, exchange = null, words = 3))
        }
    }
}
