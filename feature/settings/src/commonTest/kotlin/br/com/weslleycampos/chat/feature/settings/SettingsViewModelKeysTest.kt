package br.com.weslleycampos.chat.feature.settings

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.settings.SettingsEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.data.settings.models.ProviderKey
import br.com.weslleycampos.chat.feature.settings.resources.FeatureSettingsRes
import br.com.weslleycampos.chat.feature.settings.resources.settings_key_add
import br.com.weslleycampos.chat.feature.settings.resources.settings_key_edit
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_key_remove_failed
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_key_store_failed
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_key_stored
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Settings' "api keys" group on `SettingsViewModel`: the rows, the edit row, save and remove, and the draft. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelKeysTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val viewModels = mutableListOf<SettingsViewModel>()

    private fun keysTest(body: suspend TestScope.() -> Unit): TestResult = runTest {
        try {
            body()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun TestScope.settingsViewModel(
        keys: FakeProviderKeyRepository = FakeProviderKeyRepository(),
        navigator: NavigatorImpl = navigator(VoiceEntry(), SettingsEntry),
    ): SettingsViewModel {
        val viewModel = SettingsViewModel(
            navigator = navigator,
            accounts = FakeAccountRepository(),
            keys = keys,
            languages = FakeVoiceLanguageRepository(),
        )
        viewModels += viewModel
        runCurrent()
        return viewModel
    }

    private val SettingsViewModel.state: SettingsUiState get() = uiState.value

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    // The rows

    @Test
    fun theOneRowIsFuelixWithTheMaskedKeyTheRepositoryReads() = keysTest {
        val keys = FakeProviderKeyRepository(mapOf(ModelProvider.FUELIX to FakeKey))
        val viewModel = settingsViewModel(keys = keys)

        assertEquals(listOf(ProviderKey(ModelProvider.FUELIX, fakeMask(FakeKey))), viewModel.state.keys)
        assertEquals(listOf("Fuelix"), viewModel.state.keyRows.map { it.name })

        keys.stored.value = emptyMap()
        runCurrent()

        assertEquals(listOf(false), viewModel.state.keyRows.map { it.hasKey })
    }

    // The edit row

    @Test
    fun aTapOpensThatRowsEditRowWithAnEmptyDraftAndATapAgainClosesItAndClearsTheDraft() = keysTest {
        val viewModel = settingsViewModel()

        viewModel.onKeyRowClick(ModelProvider.FUELIX)
        assertEquals(ModelProvider.FUELIX, viewModel.state.editing)
        assertEquals("", viewModel.draft.value)
        assertEquals(listOf(true), viewModel.state.keyRows.map { it.editing })

        viewModel.onDraftChange(FakeKey)
        assertEquals(FakeKey, viewModel.draft.value)

        viewModel.onKeyRowClick(ModelProvider.FUELIX)
        assertNull(viewModel.state.editing)
        assertEquals("", viewModel.draft.value)
    }

    @Test
    fun typingWithNoRowOpenIsIgnored() = keysTest {
        val viewModel = settingsViewModel()

        viewModel.onDraftChange(FakeKey)
        viewModel.onSaveKey()
        runCurrent()

        assertEquals("", viewModel.draft.value)
    }

    // Save

    @Test
    fun saveStoresTheDraftOnceClosesTheRowAndToastsKeyEncryptedAndStoredFor1400Ms() = keysTest {
        val keys = FakeProviderKeyRepository()
        val viewModel = settingsViewModel(keys = keys)
        viewModel.onKeyRowClick(ModelProvider.FUELIX)
        viewModel.onDraftChange(" $FakeKey ")

        viewModel.onSaveKey()
        // A second save before the first answers saves nothing more: the row is already closed.
        viewModel.onSaveKey()
        assertNull(viewModel.state.editing)
        assertEquals("", viewModel.draft.value)
        runCurrent()

        assertEquals(listOf(ModelProvider.FUELIX to " $FakeKey "), keys.saves)
        assertEquals(FeatureSettingsRes.string.settings_toast_key_stored, viewModel.state.toast)
        assertEquals(fakeMask(FakeKey), viewModel.state.keyRows.first().masked)
        assertEquals(FeatureSettingsRes.string.settings_key_edit, viewModel.state.keyRows.first().action)

        advance(1399.milliseconds)
        assertEquals(FeatureSettingsRes.string.settings_toast_key_stored, viewModel.state.toast)
        advance(1.milliseconds)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun aBlankSaveKeepsTheOldKeyClosesTheRowAndShowsNoToast() = keysTest {
        val keys = FakeProviderKeyRepository(mapOf(ModelProvider.FUELIX to FakeKey))
        val viewModel = settingsViewModel(keys = keys)

        for (blank in listOf("", "   ")) {
            viewModel.onKeyRowClick(ModelProvider.FUELIX)
            viewModel.onDraftChange(blank)
            viewModel.onSaveKey()
            runCurrent()

            assertNull(viewModel.state.editing)
            assertNull(viewModel.state.toast)
            assertEquals(mapOf(ModelProvider.FUELIX to FakeKey), keys.stored.value)
            assertEquals(fakeMask(FakeKey), viewModel.state.keyRows.single().masked)
        }
    }

    @Test
    fun savingAgainReplacesTheKeyAndToastsAgain() = keysTest {
        val keys = FakeProviderKeyRepository(mapOf(ModelProvider.FUELIX to FakeKey))
        val viewModel = settingsViewModel(keys = keys)

        viewModel.onKeyRowClick(ModelProvider.FUELIX)
        viewModel.onDraftChange(OtherFakeKey)
        viewModel.onSaveKey()
        runCurrent()

        assertEquals(mapOf(ModelProvider.FUELIX to OtherFakeKey), keys.stored.value)
        assertEquals(FeatureSettingsRes.string.settings_toast_key_stored, viewModel.state.toast)
    }

    @Test
    fun aSaveOrRemoveTheStoreRefusesSaysSoAndStoresNothing() = keysTest {
        val keys = FakeProviderKeyRepository(mapOf(ModelProvider.FUELIX to FakeKey))
        val viewModel = settingsViewModel(keys = keys)
        keys.failure = IllegalStateException("keystore")

        viewModel.onKeyRowClick(ModelProvider.FUELIX)
        viewModel.onDraftChange(OtherFakeKey)
        viewModel.onSaveKey()
        runCurrent()
        assertEquals(FeatureSettingsRes.string.settings_toast_key_store_failed, viewModel.state.toast)
        assertNull(viewModel.state.editing)

        viewModel.onKeyRowClick(ModelProvider.FUELIX)
        viewModel.onRemoveKey()
        runCurrent()
        assertEquals(FeatureSettingsRes.string.settings_toast_key_remove_failed, viewModel.state.toast)
        assertEquals(mapOf(ModelProvider.FUELIX to FakeKey), keys.stored.value)
    }

    // Remove

    @Test
    fun removeRemovesTheKeyClosesTheRowAndShowsNoToast() = keysTest {
        val keys = FakeProviderKeyRepository(mapOf(ModelProvider.FUELIX to FakeKey))
        val viewModel = settingsViewModel(keys = keys)
        viewModel.onKeyRowClick(ModelProvider.FUELIX)
        assertTrue(viewModel.state.keyRows.single().removable)
        viewModel.onDraftChange(OtherFakeKey)

        viewModel.onRemoveKey()
        runCurrent()

        assertEquals(listOf(ModelProvider.FUELIX), keys.removes)
        assertEquals(emptyMap(), keys.stored.value)
        assertNull(viewModel.state.editing)
        assertEquals("", viewModel.draft.value)
        assertNull(viewModel.state.toast)
        assertEquals(FeatureSettingsRes.string.settings_key_add, viewModel.state.keyRows.single().action)
    }

    @Test
    fun removeIsOfferedAndDoesSomethingOnlyForAStoredKey() = keysTest {
        val keys = FakeProviderKeyRepository()
        val viewModel = settingsViewModel(keys = keys)
        viewModel.onKeyRowClick(ModelProvider.FUELIX)

        assertFalse(viewModel.state.keyRows.first().removable)
        viewModel.onRemoveKey()
        runCurrent()

        assertEquals(emptyList(), keys.removes)
        assertEquals(ModelProvider.FUELIX, viewModel.state.editing)
    }

    // The draft

    @Test
    fun theDraftNeverReachesTheStateTheSavedBackStackOrTheToast() = keysTest {
        val navigator = navigator(VoiceEntry(), SettingsEntry)
        val viewModel = settingsViewModel(navigator = navigator)
        val json = Json { serializersModule = SettingsEntryProvider().serializerModule() }
        viewModel.onKeyRowClick(ModelProvider.FUELIX)
        viewModel.onDraftChange(FakeKey)

        // The state the screen renders, prints and compares holds no draft.
        assertFalse(FakeKey in viewModel.state.toString())
        assertEquals(
            SettingsUiState(account = Ana.toUi(), editing = ModelProvider.FUELIX),
            viewModel.state.copy(keys = emptyList(), languages = null),
        )
        // The back stack, which is all Settings saves, holds only the key-less `SettingsEntry`.
        assertEquals(SettingsEntry, navigator.stack.last())
        val backStack = ListSerializer(PolymorphicSerializer(NavKey::class))
        val saved = json.encodeToString(backStack, listOf(navigator.stack.last()))
        assertFalse(FakeKey in saved)

        viewModel.onSaveKey()
        runCurrent()
        // The toast is the fixed copy, never the key.
        assertEquals(FeatureSettingsRes.string.settings_toast_key_stored, viewModel.state.toast)
        assertFalse(FakeKey in viewModel.state.toString())
        assertEquals("", viewModel.draft.value)
    }

    @Test
    fun leavingSettingsClearsTheDraftAndANewSettingsStartsWithNone() = keysTest {
        val navigator = navigator(VoiceEntry(), SettingsEntry)
        val viewModel = settingsViewModel(navigator = navigator)
        viewModel.onKeyRowClick(ModelProvider.FUELIX)
        viewModel.onDraftChange(FakeKey)

        viewModel.onBack()

        assertEquals("", viewModel.draft.value)
        assertNull(viewModel.state.editing)
        assertEquals(listOf<NavKey>(VoiceEntry()), navigator.stack)

        // Settings again, or rebuilt after the process died: nothing is restored.
        navigator.navigate(SettingsEntry)
        val again = settingsViewModel(navigator = navigator)
        assertEquals("", again.draft.value)
        assertNull(again.state.editing)
    }

    @Test
    fun signingOutClearsTheDraft() = keysTest {
        val viewModel = settingsViewModel()
        viewModel.onKeyRowClick(ModelProvider.FUELIX)
        viewModel.onDraftChange(FakeKey)

        viewModel.onSignOut()

        assertEquals("", viewModel.draft.value)
        assertNull(viewModel.state.editing)
    }
}
