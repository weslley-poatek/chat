package br.com.weslleycampos.chat.feature.voice.speech

import br.com.weslleycampos.chat.feature.voice.resources.FeatureVoiceRes
import br.com.weslleycampos.chat.feature.voice.resources.voice_toast_failed
import br.com.weslleycampos.chat.feature.voice.resources.voice_toast_not_allowed
import br.com.weslleycampos.chat.feature.voice.resources.voice_toast_unavailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The speech package's pure parts: where words start, the problems' toasts, and microphone access. */
class SpeechTest {
    @Test
    fun eachWordStartsAtZeroOrJustAfterASpace() {
        assertEquals(listOf(0, 4, 6, 14), wordStarts("add a leading flag"))
        assertEquals(listOf(0), wordStarts("Sure."))
        assertEquals(listOf(0), wordStarts(""))
    }

    @Test
    fun theWordStartsMatchTheWordsTheCaptionSplits() {
        for (text in listOf("add a leading flag", "Sure.", "two  spaces", " leading", "trailing ", "")) {
            val words = wordStarts(text).map { start -> text.substring(start).substringBefore(' ') }

            assertEquals(text.split(' '), words, "\"$text\"")
        }
    }

    @Test
    fun eachSpeechProblemHasTheToastThatSaysWhy() {
        assertEquals(
            listOf(
                FeatureVoiceRes.string.voice_toast_not_allowed,
                FeatureVoiceRes.string.voice_toast_unavailable,
                FeatureVoiceRes.string.voice_toast_failed,
            ),
            SpeechProblem.entries.map { it.toast },
        )
    }

    @Test
    fun aSpeechExceptionCarriesItsProblemAndCause() {
        val cause = IllegalStateException("audio session")

        val exception = SpeechException(SpeechProblem.FAILED, cause)

        assertEquals(SpeechProblem.FAILED, exception.problem)
        assertSame(cause, exception.cause)
        assertEquals("Speech failed: FAILED", exception.message)
    }

    @Test
    fun whatWasHeardIsItsText() {
        assertEquals("so the", Heard.Partial("so the").text)
        assertEquals("so the hook works", Heard.Final("so the hook works").text)
    }

    @Test
    fun grantedMicrophoneSaysWhatWasReadAndAsksThroughItsPrompt() {
        var asked = 0
        val denied = GrantedMicrophone(granted = false) { asked++ }

        assertFalse(denied.granted)
        denied.request()
        assertEquals(1, asked)
        assertTrue(GrantedMicrophone(granted = true) {}.granted)
    }
}
