package br.com.weslleycampos.chat.data.settings

import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthManager
import br.com.weslleycampos.chat.data.settings.models.Account
import kotlinx.coroutines.flow.Flow

/**
 * Settings' "account" group: who is signed in and how, signing out, and deleting the account.
 *
 * Conversations, the model picker's choices and the provider keys belong to this device, and
 * nothing syncs: signing out keeps them all, and only deleting the account wipes them.
 */
interface AccountRepository {
    /**
     * The signed-in [Account], or `null` while nobody is signed in. Emits the current value as soon
     * as it is collected and again whenever the user, or how they sign in, changes; never the same
     * value twice in a row.
     */
    fun observeAccount(): Flow<Account?>

    /**
     * Signs out of Firebase, as [AuthManager.signOut] does: [observeAccount] then emits `null`.
     * Every conversation, choice and key stays on this device for the next sign-in.
     */
    fun signOut()

    /**
     * Deletes the Firebase account and then, only once that succeeded, everything this device
     * keeps: every provider key, every conversation with its messages, and the favorites and
     * default model. The persona presets stay. When it returns, Firebase has signed the user out,
     * so [observeAccount] emits `null`.
     *
     * When Firebase refuses, it throws that [AuthException] and wipes nothing, so a failed delete
     * loses nothing: [AuthException.RequiresRecentLogin] (sign in again first),
     * [AuthException.NotSignedIn], [AuthException.NetworkError], [AuthException.TooManyRequests],
     * [AuthException.UserNotFound] or [AuthException.Unknown]. A `CancellationException` from
     * Firebase is rethrown untouched, also wiping nothing.
     *
     * Cancelling the caller neither stops the delete nor the wipe after it: once Firebase is
     * asked, the call runs to its end and then throws the caller's `CancellationException`, so a
     * deleted account always leaves a wiped device behind.
     */
    suspend fun deleteAccount()
}
