package br.com.weslleycampos.chat.feature.settings

import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.entries.settings.SettingsEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import br.com.weslleycampos.chat.feature.settings.resources.FeatureSettingsRes
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_language_failed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings' "voice" group on `SettingsViewModel`: the languages voice mode listens and speaks in. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelLanguageTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val viewModels = mutableListOf<SettingsViewModel>()

    private val portuguese = VoiceLanguages(VoiceLanguage.PORTUGUESE_BRAZIL)
    private val english = VoiceLanguages(VoiceLanguage.ENGLISH_US)

    private fun languageTest(body: suspend TestScope.() -> Unit): TestResult = runTest {
        try {
            body()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun TestScope.settingsViewModel(
        languages: FakeVoiceLanguageRepository = FakeVoiceLanguageRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
    ): SettingsViewModel {
        val viewModel = SettingsViewModel(
            navigator = navigator(VoiceEntry(), SettingsEntry),
            accounts = accounts,
            keys = FakeProviderKeyRepository(),
            languages = languages,
        )
        viewModels += viewModel
        runCurrent()
        return viewModel
    }

    private val SettingsViewModel.state: SettingsUiState get() = uiState.value

    @Test
    fun theVoiceGroupShowsTheLanguagesVoiceModeIsInAndFollowsTheirChanges() = languageTest {
        val languages = FakeVoiceLanguageRepository(portuguese)
        val viewModel = settingsViewModel(languages = languages)
        assertEquals(portuguese, viewModel.state.languages)

        languages.languages.value = english.copy(bilingual = true)
        runCurrent()

        assertEquals(english.copy(bilingual = true), viewModel.state.languages)
    }

    @Test
    fun tappingASegmentPicksItsLanguagesForVoiceMode() = languageTest {
        val languages = FakeVoiceLanguageRepository(portuguese)
        val viewModel = settingsViewModel(languages = languages)

        viewModel.voice.onLanguagesClick(english)
        runCurrent()
        val (_, _, both) = assertNotNull(viewModel.state.languages).options()
        viewModel.voice.onLanguagesClick(both)
        runCurrent()

        assertEquals(listOf(english, english.copy(bilingual = true)), languages.picks)
        assertEquals(english.copy(bilingual = true), viewModel.state.languages)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun languagesThatCannotBeStoredToastAndTheGroupKeepsThoseVoiceModeIsIn() = languageTest {
        val languages = FakeVoiceLanguageRepository(portuguese).apply {
            failure = IllegalStateException("disk full")
        }
        val viewModel = settingsViewModel(languages = languages)

        viewModel.voice.onLanguagesClick(english)
        runCurrent()

        assertEquals(FeatureSettingsRes.string.settings_toast_language_failed, viewModel.state.toast)
        assertEquals(portuguese, viewModel.state.languages)
    }

    @Test
    fun languagesThatCannotBeReadLeaveTheVoiceGroupHidden() = languageTest {
        val unreadable = object : FakeVoiceLanguageRepository() {
            override fun observeLanguages(): Flow<VoiceLanguages> = flow { throw IllegalStateException("unreadable") }
        }
        val viewModel = settingsViewModel(languages = unreadable)

        assertNull(viewModel.state.languages)
    }

    @Test
    fun nothingIsPickedWhileTheAccountIsBeingDeleted() = languageTest {
        val pending = CompletableDeferred<Unit>()
        val accounts = FakeAccountRepository().apply { delete = { pending.await() } }
        val languages = FakeVoiceLanguageRepository(portuguese)
        val viewModel = settingsViewModel(languages = languages, accounts = accounts)
        viewModel.onDeleteAccount()
        viewModel.onDeleteAccount()
        runCurrent()
        assertTrue(viewModel.state.deleting)

        viewModel.voice.onLanguagesClick(english)
        runCurrent()

        assertEquals(emptyList(), languages.picks)
        assertEquals(portuguese, viewModel.state.languages)
    }
}
