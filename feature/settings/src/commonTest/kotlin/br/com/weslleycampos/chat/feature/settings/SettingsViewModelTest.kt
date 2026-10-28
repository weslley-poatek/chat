package br.com.weslleycampos.chat.feature.settings

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.auth.SignInEntry
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.core.navigation.entries.settings.SettingsEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthUser
import br.com.weslleycampos.chat.data.auth.SignInMethod
import br.com.weslleycampos.chat.data.settings.models.Account
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.feature.settings.resources.FeatureSettingsRes
import br.com.weslleycampos.chat.feature.settings.resources.settings_delete_confirm
import br.com.weslleycampos.chat.feature.settings.resources.settings_deleting
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_failed
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_network
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_recent_login
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_too_many_requests
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Settings' "account" group and navigation on `SettingsViewModel`: sign out, delete account and back. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val viewModels = mutableListOf<SettingsViewModel>()

    /** `runTest`, after which the screen leaves and each view model's scope ends. */
    private fun settingsTest(body: suspend TestScope.() -> Unit): TestResult = runTest {
        try {
            body()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun TestScope.settingsViewModel(
        accounts: FakeAccountRepository = FakeAccountRepository(),
        keys: FakeProviderKeyRepository = FakeProviderKeyRepository(),
        navigator: NavigatorImpl = navigator(VoiceEntry(), SettingsEntry),
    ): SettingsViewModel {
        val viewModel = SettingsViewModel(navigator = navigator, accounts = accounts, keys = keys)
        viewModels += viewModel
        runCurrent()
        return viewModel
    }

    private val SettingsViewModel.state: SettingsUiState get() = uiState.value

    private val SettingsViewModel.stack: List<NavKey> get() = (navigator as NavigatorImpl).stack

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    // The account

    @Test
    fun theAccountGroupShowsTheSignedInUserAndHowTheySignIn() = settingsTest {
        val accounts = FakeAccountRepository()
        val viewModel = settingsViewModel(accounts = accounts)

        assertEquals(
            AccountUi(
                initials = "AR",
                name = "Ana Ribeiro",
                email = "ana.ribeiro@example.dev",
                signInMethods = "Google",
            ),
            viewModel.state.account,
        )

        accounts.account.value = Account(
            user = AuthUser(id = "you", displayName = null, email = "you@example.dev", photoUrl = null),
            signInMethods = listOf(SignInMethod.GOOGLE, SignInMethod.EMAIL_PASSWORD),
        )
        runCurrent()

        assertEquals(
            AccountUi(initials = "Y", name = "you", email = "you@example.dev", signInMethods = "Google, email"),
            viewModel.state.account,
        )
    }

    // Sign out

    @Test
    fun signOutSignsOutAndOpensSignInWithTheWholeStackCleared() = settingsTest {
        val stacks: List<List<NavKey>> = listOf(
            listOf(VoiceEntry(), SettingsEntry),
            listOf(VoiceEntry(conversationId = 7), HistoryEntry, SettingsEntry),
            listOf(SettingsEntry),
        )
        for (stack in stacks) {
            val accounts = FakeAccountRepository()
            val keys = FakeProviderKeyRepository(mapOf(ModelProvider.ANTHROPIC to FakeKey))
            val navigator = navigator(*stack.toTypedArray())
            val viewModel = settingsViewModel(accounts = accounts, keys = keys, navigator = navigator)

            viewModel.onSignOut()
            // The account's `null` that follows navigates no further.
            runCurrent()
            viewModel.onSignOut()

            assertEquals(1, accounts.signOuts)
            assertEquals(listOf<NavKey>(SignInEntry()), navigator.stack)
            // Signing out keeps the keys on this device.
            assertEquals(mapOf(ModelProvider.ANTHROPIC to FakeKey), keys.stored.value)
        }
    }

    @Test
    fun anAccountSignedOutBehindSettingsOpensSignInWithTheStackCleared() = settingsTest {
        val accounts = FakeAccountRepository()
        val viewModel = settingsViewModel(
            accounts = accounts,
            navigator = navigator(VoiceEntry(), HistoryEntry, SettingsEntry),
        )

        accounts.account.value = null
        runCurrent()

        assertEquals(listOf<NavKey>(SignInEntry()), viewModel.stack)
        assertEquals(0, accounts.signOuts)
    }

    // Delete account

    @Test
    fun theFirstDeleteTapArmsItFor3000MsThenItDisarmsAndDeletesNothing() = settingsTest {
        val accounts = FakeAccountRepository()
        val viewModel = settingsViewModel(accounts = accounts)
        val before = viewModel.stack

        viewModel.onDeleteAccount()
        runCurrent()
        assertTrue(viewModel.state.deleteArmed)
        assertEquals(FeatureSettingsRes.string.settings_delete_confirm, viewModel.state.deleteHint)

        advance(2999.milliseconds)
        assertTrue(viewModel.state.deleteArmed)

        advance(1.milliseconds)
        assertFalse(viewModel.state.deleteArmed)
        assertNull(viewModel.state.deleteHint)
        assertEquals(0, accounts.deletes)
        assertEquals(before, viewModel.stack)
    }

    @Test
    fun aTapAfterTheArmingExpiredArmsItAgainForAWhole3000Ms() = settingsTest {
        val accounts = FakeAccountRepository()
        val viewModel = settingsViewModel(accounts = accounts)

        viewModel.onDeleteAccount()
        advance(3000.milliseconds)
        viewModel.onDeleteAccount()
        advance(2999.milliseconds)

        assertTrue(viewModel.state.deleteArmed)
        assertEquals(0, accounts.deletes)
        advance(1.milliseconds)
        assertFalse(viewModel.state.deleteArmed)
    }

    @Test
    fun aSecondTapWithin3000MsDeletesTheAccountAndOpensSignUpWithTheWholeStackCleared() = settingsTest {
        for (stack in listOf(listOf(VoiceEntry(), SettingsEntry), listOf(VoiceEntry(), HistoryEntry, SettingsEntry))) {
            val accounts = FakeAccountRepository()
            val navigator = navigator(*stack.toTypedArray())
            val viewModel = settingsViewModel(accounts = accounts, navigator = navigator)

            viewModel.onDeleteAccount()
            advance(2999.milliseconds)
            viewModel.onDeleteAccount()
            runCurrent()

            assertEquals(1, accounts.deletes)
            // The prototype's `authMode:'signup'`, with nothing to go back to.
            assertEquals(listOf<NavKey>(SignInEntry(signUp = true)), navigator.stack)
            assertFalse(navigator.navigateUp())
            // The armed time running out afterwards changes nothing.
            advance(3000.milliseconds)
            assertEquals(listOf<NavKey>(SignInEntry(signUp = true)), navigator.stack)
        }
    }

    @Test
    fun whileTheDeleteRunsTheRowIsBusyAndNothingElseAnswers() = settingsTest {
        val pending = CompletableDeferred<Unit>()
        val accounts = FakeAccountRepository().apply { delete = { pending.await() } }
        val keys = FakeProviderKeyRepository()
        val navigator = navigator(VoiceEntry(), HistoryEntry, SettingsEntry)
        val viewModel = settingsViewModel(accounts = accounts, keys = keys, navigator = navigator)
        viewModel.onKeyRowClick(ModelProvider.OPENAI)
        viewModel.onDraftChange(FakeKey)

        viewModel.onDeleteAccount()
        viewModel.onDeleteAccount()
        runCurrent()

        assertTrue(viewModel.state.deleting)
        assertFalse(viewModel.state.deleteArmed)
        assertEquals(FeatureSettingsRes.string.settings_deleting, viewModel.state.deleteHint)
        // The edit row closed with its draft, so no key is saved after the wipe.
        assertNull(viewModel.state.editing)
        assertEquals("", viewModel.draft.value)

        viewModel.onDeleteAccount()
        viewModel.onDeleteAccount()
        viewModel.onSignOut()
        viewModel.onBack()
        viewModel.onKeyRowClick(ModelProvider.OPENAI)
        viewModel.onDraftChange(FakeKey)
        viewModel.onSaveKey()
        advance(10_000.milliseconds)

        assertEquals(1, accounts.deletes)
        assertEquals(0, accounts.signOuts)
        assertTrue(viewModel.state.deleting)
        assertNull(viewModel.state.editing)
        assertEquals(emptyList(), keys.saves)
        assertEquals(listOf(VoiceEntry(), HistoryEntry, SettingsEntry), navigator.stack)

        pending.complete(Unit)
        runCurrent()

        assertEquals(listOf<NavKey>(SignInEntry(signUp = true)), navigator.stack)
    }

    @Test
    fun aDeleteFirebaseRefusesSaysWhyFor1400MsAndTheUserStaysSignedInOnSettings() = settingsTest {
        val refusals: List<Pair<Exception, StringResource>> = listOf(
            AuthException.RequiresRecentLogin(message = null, cause = null) to
                FeatureSettingsRes.string.settings_toast_delete_recent_login,
            AuthException.NetworkError(message = null, cause = null) to
                FeatureSettingsRes.string.settings_toast_delete_network,
            AuthException.TooManyRequests(message = null, cause = null) to
                FeatureSettingsRes.string.settings_toast_delete_too_many_requests,
            AuthException.UserNotFound(message = null, cause = null) to
                FeatureSettingsRes.string.settings_toast_delete_failed,
            AuthException.Unknown(message = null, cause = null) to
                FeatureSettingsRes.string.settings_toast_delete_failed,
        )
        for ((refusal, message) in refusals) {
            val accounts = FakeAccountRepository().apply { delete = { throw refusal } }
            val keys = FakeProviderKeyRepository(mapOf(ModelProvider.ANTHROPIC to FakeKey))
            val navigator = navigator(VoiceEntry(), SettingsEntry)
            val viewModel = settingsViewModel(accounts = accounts, keys = keys, navigator = navigator)

            viewModel.onDeleteAccount()
            viewModel.onDeleteAccount()
            runCurrent()

            assertEquals(message, viewModel.state.toast)
            assertFalse(viewModel.state.deleting)
            assertFalse(viewModel.state.deleteArmed)
            assertEquals(Ana.toUi(), viewModel.state.account)
            assertEquals(listOf(VoiceEntry(), SettingsEntry), navigator.stack)
            assertEquals(mapOf(ModelProvider.ANTHROPIC to FakeKey), keys.stored.value)

            advance(1399.milliseconds)
            assertEquals(message, viewModel.state.toast)
            advance(1.milliseconds)
            assertNull(viewModel.state.toast)

            // The row works again: two taps try once more.
            viewModel.onDeleteAccount()
            viewModel.onDeleteAccount()
            runCurrent()
            assertEquals(2, accounts.deletes)
        }
    }

    @Test
    fun aDeleteFirebaseCancelsLeavesTheRowUsableWithNoToast() = settingsTest {
        val accounts = FakeAccountRepository().apply { delete = { throw CancellationException("cancelled") } }
        val viewModel = settingsViewModel(accounts = accounts)

        viewModel.onDeleteAccount()
        viewModel.onDeleteAccount()
        runCurrent()

        assertFalse(viewModel.state.deleting)
        assertNull(viewModel.state.toast)
        assertEquals(listOf(VoiceEntry(), SettingsEntry), viewModel.stack)
    }

    @Test
    fun aWipeThatFailsAfterTheAccountWasDeletedSaysSoAndStillOpensSignIn() = settingsTest {
        val accounts = FakeAccountRepository()
        accounts.delete = {
            // Firebase deleted the account and signed out; then the device's wipe failed.
            accounts.account.value = null
            error("disk")
        }
        val viewModel = settingsViewModel(accounts = accounts)

        viewModel.onDeleteAccount()
        viewModel.onDeleteAccount()
        runCurrent()

        assertEquals(FeatureSettingsRes.string.settings_toast_delete_failed, viewModel.state.toast)
        assertEquals(listOf<NavKey>(SignInEntry()), viewModel.stack)
    }

    // Back

    @Test
    fun backReturnsToTheScreenUnderneathVoiceOrHistoryAsItWas() = settingsTest {
        val overVoice = settingsViewModel(navigator = navigator(VoiceEntry(conversationId = 7), SettingsEntry))
        val overHistory = settingsViewModel(navigator = navigator(VoiceEntry(), HistoryEntry, SettingsEntry))

        overVoice.onBack()
        overHistory.onBack()

        assertEquals(listOf<NavKey>(VoiceEntry(conversationId = 7)), overVoice.stack)
        assertEquals(listOf(VoiceEntry(), HistoryEntry), overHistory.stack)
    }

    @Test
    fun settingsAloneGivesWayToANewVoiceSessionOnBack() = settingsTest {
        val alone = settingsViewModel(navigator = navigator(SettingsEntry))

        alone.onBack()

        // Never a dead end: Voice, the home screen, replaces Settings.
        assertEquals(listOf<NavKey>(VoiceEntry()), alone.stack)
    }
}
