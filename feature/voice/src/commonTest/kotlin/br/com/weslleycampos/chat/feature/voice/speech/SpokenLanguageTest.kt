package br.com.weslleycampos.chat.feature.voice.speech

import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import kotlin.test.Test
import kotlin.test.assertEquals

class SpokenLanguageTest {
    private val portuguese = VoiceLanguage.PORTUGUESE_BRAZIL
    private val english = VoiceLanguage.ENGLISH_US
    private val both = listOf(portuguese, english)

    @Test
    fun aReplyIsWrittenInTheLanguageWhoseCommonWordsItHasCodeTermsCountingForNeither() {
        assertEquals(portuguese, writtenIn("Use uma flag de leading edge no useEffect, e limpe o timer.", both))
        assertEquals(english, writtenIn("Add a leading flag to the useEffect, then clear the timer.", both))
        assertEquals(portuguese, writtenIn("Não, o debounce só dispara depois do último evento.", both))
        assertEquals(english, writtenIn("It's the trailing edge that fires, so don't worry about it.", both))
    }

    @Test
    fun portuguesesAccentedWordsCountForIt() {
        assertEquals(portuguese, writtenIn("Configuração padrão.", both))
    }

    @Test
    fun whenNothingTellsThemApartItIsTheFirstLanguage() {
        assertEquals(portuguese, writtenIn("useEffect useState", both))
        assertEquals(english, writtenIn("useEffect useState", listOf(english, portuguese)))
        assertEquals(english, writtenIn("", listOf(english, portuguese)))
    }

    @Test
    fun oneLanguageSaysEveryReplyInItAndBilingualSaysEachInTheOneItIsWrittenIn() {
        val written = "Use uma flag de leading edge no hook."
        assertEquals(english, VoiceLanguages(english).voiceFor(written))
        assertEquals(portuguese, VoiceLanguages(english, bilingual = true).voiceFor(written))
        assertEquals(english, VoiceLanguages(portuguese, bilingual = true).voiceFor("Add the flag to the hook."))
    }
}
