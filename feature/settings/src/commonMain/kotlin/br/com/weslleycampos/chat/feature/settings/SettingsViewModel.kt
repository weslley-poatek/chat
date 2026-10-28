package br.com.weslleycampos.chat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.auth.SignInEntry
import br.com.weslleycampos.chat.core.navigation.entries.settings.SettingsEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.data.settings.AccountRepository
import br.com.weslleycampos.chat.data.settings.ProviderKeyRepository
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.feature.settings.resources.FeatureSettingsRes
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_key_remove_failed
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_key_store_failed
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_key_stored
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration

/**
 * The Settings frame's logic, reduced to the "account" and "api keys" groups: the prototype's
 * `signOut`, `deleteAccount` with its `deleteArmed` timer, and `keyRows`' edit, save and remove
 * (860–861, 911–920), on [AccountRepository] and [ProviderKeyRepository].
 *
 * Navigation: Settings opens over Voice or History, and back returns there. Signing out opens
 * sign-in, and deleting the account opens account creation, each with the whole stack cleared,
 * since what was under Settings belonged to the account. An account that signs out by itself (a
 * session Firebase revoked) opens sign-in too.
 *
 * The key the user types is [draft]: in this ViewModel's memory only, never in [uiState], saved
 * state, a navigation key, a toast or a log. It is passed once to [ProviderKeyRepository.save] and
 * cleared on save, on closing its row, on remove and on leaving.
 */
@KoinViewModel(binds = [SettingsViewModel::class])
class SettingsViewModel(
    val navigator: Navigator,
    private val accounts: AccountRepository,
    private val keys: ProviderKeyRepository,
) : ViewModel(), Navigator by navigator {
    val uiState: StateFlow<SettingsUiState>
        field = MutableStateFlow(SettingsUiState())

    /** What is typed in the open edit row, the prototype's `keyDraft`. Empty while no row is open. */
    val draft: StateFlow<String>
        field = MutableStateFlow("")

    /** The prototype's `toastMsg`: a new toast replaces the shown one and restarts its time. */
    private val toast = TimedValue<StringResource>(viewModelScope, ChatToastDefaults.Duration) { message ->
        uiState.update { it.copy(toast = message) }
    }

    /** Ends the armed "Delete account" after [DeleteArmDuration], the prototype's `timers.arm`. */
    private var disarm: Job? = null

    /** The last account read was `null`: nobody is signed in any more. */
    private var signedOut = false

    /** Settings has handed the stack to sign-in, so nothing navigates again. */
    private var left = false

    init {
        viewModelScope.launch {
            accounts.observeAccount().collect { account ->
                signedOut = account == null
                if (account != null) {
                    uiState.update { it.copy(account = account.toUi()) }
                } else if (!uiState.value.deleting) {
                    // Signed out while Settings shows: a delete that ran signs out too, and opens
                    // sign-up itself.
                    leave(SignInEntry())
                }
            }
        }
        viewModelScope.launch {
            keys.observeKeys()
                // The encrypted store could not be read (a Keystore or Keychain failure): the group
                // stays unread rather than showing every provider as not set.
                .catch { }
                .collect { rows -> uiState.update { it.copy(keys = rows) } }
        }
    }

    /**
     * The back chevron, as system back and the iOS edge swipe: the screen underneath, as it was.
     * Settings alone gives way to a new voice session. Ignored while the account is being deleted.
     */
    fun onBack() {
        if (uiState.value.deleting) return
        closeEdit()
        if (!navigateUp()) navigate(VoiceEntry()) { popUpTo<SettingsEntry> { inclusive = true } }
    }

    /**
     * "Sign out", the prototype's `signOut`: Firebase signs out and sign-in replaces the whole
     * stack. Conversations, choices and keys stay on this device for the next sign-in.
     */
    fun onSignOut() {
        if (uiState.value.deleting || left) return
        accounts.signOut()
        leave(SignInEntry())
    }

    /**
     * "Delete account", the prototype's `deleteAccount`. A first tap arms it for
     * [DeleteArmDuration] ("tap again to confirm"); a second tap within that time deletes the
     * account, then the keys and the chat's data, and opens account creation with the stack
     * cleared. While it runs the row is busy and every other control waits. When Firebase refuses,
     * nothing is deleted, the toast says why and the user stays signed in.
     */
    fun onDeleteAccount() {
        val state = uiState.value
        if (state.deleting || state.account == null || left) return
        if (!state.deleteArmed) {
            // The first tap arms it, and the prototype's `timers.arm` disarms it.
            disarm?.cancel()
            uiState.update { it.copy(deleteArmed = true) }
            disarm = viewModelScope.launch {
                delay(DeleteArmDuration)
                uiState.update { it.copy(deleteArmed = false) }
            }
            return
        }
        disarm?.cancel()
        closeEdit()
        uiState.update { it.copy(deleteArmed = false, deleting = true) }
        viewModelScope.launch {
            try {
                accounts.deleteAccount()
                leave(SignInEntry(signUp = true))
            } catch (e: CancellationException) {
                // A cancelled Firebase task arrives as cancellation too; never leave the row stuck busy.
                uiState.update { it.copy(deleting = false) }
                throw e
            } catch (e: Exception) {
                uiState.update { it.copy(deleting = false) }
                toast.show(e.deleteFailureMessage())
                // Only a wipe that failed after Firebase deleted the account gets here signed out.
                if (signedOut) leave(SignInEntry())
            }
        }
    }

    /**
     * A tap on a provider's row, the prototype's `k.edit`: its edit row opens, closing any other,
     * or closes when it was open. Either way the draft starts empty.
     */
    fun onKeyRowClick(provider: ModelProvider) {
        if (uiState.value.deleting) return
        draft.value = ""
        uiState.update { state -> state.copy(editing = if (state.editing == provider) null else provider) }
    }

    /** Typing in the open edit row. */
    fun onDraftChange(text: String) {
        if (uiState.value.editing != null) draft.value = text
    }

    /**
     * "save" (and the keyboard's Done), the prototype's `k.save`: the draft goes to the repository
     * once, the row closes and the draft is cleared. The toast says "key encrypted and stored" only
     * when a key was stored; a blank draft stores nothing and keeps the provider's key.
     */
    fun onSaveKey() {
        val provider = uiState.value.editing ?: return
        val key = draft.value
        closeEdit()
        viewModelScope.launch {
            val stored = toast.showOnFailure(FeatureSettingsRes.string.settings_toast_key_store_failed) {
                keys.save(provider, key)
            }
            if (stored) toast.show(FeatureSettingsRes.string.settings_toast_key_stored)
        }
    }

    /** "remove", the prototype's `k.remove`, shown only for a stored key: the key goes and the row closes. */
    fun onRemoveKey() {
        val state = uiState.value
        val provider = state.editing ?: return
        if (!state.hasKey(provider)) return
        closeEdit()
        viewModelScope.launch {
            toast.showOnFailure(FeatureSettingsRes.string.settings_toast_key_remove_failed) { keys.remove(provider) }
        }
    }

    override fun onCleared() {
        draft.value = ""
        super.onCleared()
    }

    /** Closes the edit row and forgets what was typed in it. */
    private fun closeEdit() {
        draft.value = ""
        uiState.update { it.copy(editing = null) }
    }

    /** Replaces the whole stack with [entry], once. */
    private fun leave(entry: SignInEntry) {
        if (left) return
        left = true
        closeEdit()
        disarm?.cancel()
        navigate(entry) { popUpTo(navBackStack.first()) { inclusive = true } }
    }
}

/** Runs a key action; a failure shows [failure] and counts as nothing done. */
private suspend fun TimedValue<StringResource>.showOnFailure(
    failure: StringResource,
    action: suspend () -> Boolean,
): Boolean = try {
    action()
} catch (e: CancellationException) {
    throw e
} catch (ignored: Exception) {
    show(failure)
    false
}

/**
 * A value shown for [duration] and then cleared, through [onChange]: showing another one restarts
 * the time. History's and the chat's toasts work the same way.
 */
internal class TimedValue<T : Any>(
    private val scope: CoroutineScope,
    private val duration: Duration,
    private val onChange: (T?) -> Unit,
) {
    private var job: Job? = null

    fun show(value: T) {
        job?.cancel()
        onChange(value)
        job = scope.launch {
            delay(duration)
            onChange(null)
        }
    }
}
