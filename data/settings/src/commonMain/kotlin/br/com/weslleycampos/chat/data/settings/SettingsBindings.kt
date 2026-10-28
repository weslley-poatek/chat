package br.com.weslleycampos.chat.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import br.com.weslleycampos.chat.core.preferences.qualifiers.EncryptedPreferences
import br.com.weslleycampos.chat.data.auth.AccountManager
import br.com.weslleycampos.chat.data.auth.AuthManager
import br.com.weslleycampos.chat.data.chat.ChatDataEraser
import org.koin.core.annotation.Single

/** The app's one [ProviderKeyRepository], on the encrypted DataStore. */
@Single
internal fun providerKeyRepository(
    @EncryptedPreferences preferences: DataStore<Preferences>,
): ProviderKeyRepository = EncryptedProviderKeyRepository(preferences = preferences)

/**
 * The app's one [AccountRepository]: the account from `:data:auth`'s managers, and a delete that
 * wipes this device's keys and the chat's data after it.
 */
@Single
internal fun accountRepository(
    auth: AuthManager,
    accounts: AccountManager,
    keys: ProviderKeyRepository,
    chatData: ChatDataEraser,
): AccountRepository = FirebaseAccountRepository(
    auth = auth,
    accounts = accounts,
    keys = keys,
    chatData = chatData,
)
