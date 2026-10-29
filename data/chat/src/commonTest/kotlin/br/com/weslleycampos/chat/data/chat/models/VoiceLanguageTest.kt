package br.com.weslleycampos.chat.data.chat.models

import br.com.weslleycampos.chat.data.chat.replies.ReplyPrompts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceLanguageTest {
    @Test
    fun theLanguagesAreBrazilianPortugueseAndAmericanEnglishByTheirTags() {
        assertEquals(listOf("pt-BR", "en-US"), VoiceLanguage.entries.map { it.tag })
        assertEquals(listOf("Português (Brasil)", "English (US)"), VoiceLanguage.entries.map { it.displayName })
        for (language in VoiceLanguage.entries) assertEquals(language, VoiceLanguage.ofTag(language.tag))
        assertNull(VoiceLanguage.ofTag("pt-PT"))
    }

    @Test
    fun aLanguageAnEngineHeardIsReadByItsLanguageAlone() {
        assertEquals(VoiceLanguage.ENGLISH_US, VoiceLanguage.heardAs("en-US"))
        assertEquals(VoiceLanguage.ENGLISH_US, VoiceLanguage.heardAs("en"))
        assertEquals(VoiceLanguage.PORTUGUESE_BRAZIL, VoiceLanguage.heardAs("pt_BR"))
        assertNull(VoiceLanguage.heardAs("es-ES"))
    }

    @Test
    fun oneLanguageListensForItAloneAndBilingualForBothThePrimaryFirst() {
        assertEquals(listOf(VoiceLanguage.ENGLISH_US), VoiceLanguages(VoiceLanguage.ENGLISH_US).all)
        assertEquals(
            listOf(VoiceLanguage.PORTUGUESE_BRAZIL, VoiceLanguage.ENGLISH_US),
            VoiceLanguages(VoiceLanguage.PORTUGUESE_BRAZIL, bilingual = true).all,
        )
        assertEquals(
            listOf(VoiceLanguage.ENGLISH_US, VoiceLanguage.PORTUGUESE_BRAZIL),
            VoiceLanguages(VoiceLanguage.ENGLISH_US, bilingual = true).all,
        )
    }

    @Test
    fun theVoicePromptAsksForAShortSpokenAnswerInTheLanguage() {
        val portuguese = ReplyPrompts.voicePrompt(VoiceLanguages(VoiceLanguage.PORTUGUESE_BRAZIL))
        val english = ReplyPrompts.voicePrompt(VoiceLanguages(VoiceLanguage.ENGLISH_US))

        assertTrue("Answer in Brazilian Portuguese" in portuguese, portuguese)
        assertTrue("Answer in American English" in english, english)
        for (prompt in listOf(portuguese, english)) {
            assertTrue("one to three short sentences" in prompt, prompt)
            assertTrue("no Markdown, lists or code" in prompt, prompt)
        }
    }

    @Test
    fun bilingualTheVoicePromptAsksForTheLanguageOfTheUsersLastMessageWithTechnicalTermsInEnglish() {
        val prompt = ReplyPrompts.voicePrompt(VoiceLanguages(VoiceLanguage.PORTUGUESE_BRAZIL, bilingual = true))

        assertTrue("speaks Brazilian Portuguese and American English" in prompt, prompt)
        assertTrue("answer in the language of their last message" in prompt, prompt)
        assertTrue("keeping code and technical terms in English" in prompt, prompt)
        assertTrue("one to three short sentences" in prompt, prompt)
    }
}
