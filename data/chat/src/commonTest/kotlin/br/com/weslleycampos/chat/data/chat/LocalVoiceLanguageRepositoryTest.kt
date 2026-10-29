package br.com.weslleycampos.chat.data.chat

import androidx.datastore.preferences.core.preferencesOf
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The voice languages Settings picks, on a fake plain DataStore and a device language the test sets. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalVoiceLanguageRepositoryTest {
    private val preferences = FakePreferences()

    /** The device's language tag, as the repository reads it. */
    private var device = "pt-BR"

    private val languages = LocalVoiceLanguageRepository(preferences = preferences, deviceLanguage = { device })

    private val portuguese = VoiceLanguages(VoiceLanguage.PORTUGUESE_BRAZIL)
    private val english = VoiceLanguages(VoiceLanguage.ENGLISH_US)

    @Test
    fun untilPickedTheLanguageIsTheDevicesAloneBrazilianPortugueseForAnyPortugueseElseEnglish() = runTest {
        for ((tag, language) in listOf(
            "pt-BR" to VoiceLanguage.PORTUGUESE_BRAZIL,
            "pt-PT" to VoiceLanguage.PORTUGUESE_BRAZIL,
            "pt_BR" to VoiceLanguage.PORTUGUESE_BRAZIL,
            "en-US" to VoiceLanguage.ENGLISH_US,
            "en-GB" to VoiceLanguage.ENGLISH_US,
            "es-ES" to VoiceLanguage.ENGLISH_US,
        )) {
            device = tag
            assertEquals(VoiceLanguages(language), languages.observeLanguages().first(), tag)
        }
        assertEquals(0, preferences.writes)
    }

    @Test
    fun pickedLanguagesAreStoredAsThePrimarysTagAndWhetherBilingualAndOutlastTheDevicesLanguage() = runTest {
        languages.setLanguages(VoiceLanguages(VoiceLanguage.ENGLISH_US, bilingual = true))
        device = "pt-PT"

        assertEquals(VoiceLanguages(VoiceLanguage.ENGLISH_US, bilingual = true), languages.observeLanguages().first())
        assertEquals(
            preferencesOf(
                LocalVoiceLanguageRepository.LanguageKey to "en-US",
                LocalVoiceLanguageRepository.BilingualKey to true,
            ),
            preferences.stored.value,
        )
        val reopened = LocalVoiceLanguageRepository(preferences = preferences, deviceLanguage = { "pt-BR" })
        assertEquals(VoiceLanguages(VoiceLanguage.ENGLISH_US, bilingual = true), reopened.observeLanguages().first())

        languages.setLanguages(portuguese)
        assertEquals(portuguese, reopened.observeLanguages().first())
    }

    @Test
    fun aStoredLanguageThisBuildDoesNotHaveReadsAsTheDevices() = runTest {
        preferences.stored.value = preferencesOf(
            LocalVoiceLanguageRepository.LanguageKey to "fr-FR",
            LocalVoiceLanguageRepository.BilingualKey to true,
        )

        assertEquals(portuguese.copy(bilingual = true), languages.observeLanguages().first())
    }

    @Test
    fun itEmitsAtOnceAndOnEachChangeButNeverTheSameLanguagesTwiceInARow() = runTest {
        val seen = mutableListOf<VoiceLanguages>()
        val collecting = launch(UnconfinedTestDispatcher(testScheduler)) { languages.observeLanguages().toList(seen) }

        languages.setLanguages(portuguese)
        languages.setLanguages(english)
        languages.setLanguages(english)
        languages.setLanguages(english.copy(bilingual = true))
        collecting.cancel()

        assertEquals(listOf(portuguese, english, english.copy(bilingual = true)), seen)
    }
}
