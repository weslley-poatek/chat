package br.com.weslleycampos.chat.data.settings

import br.com.weslleycampos.chat.data.auth.AccountManager
import br.com.weslleycampos.chat.data.auth.AuthManager
import br.com.weslleycampos.chat.data.chat.ChatDataEraser
import br.com.weslleycampos.chat.data.settings.models.Account
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * [AccountRepository] on `:data:auth`'s Firebase managers: the user from [auth], how they sign in
 * and the delete from [accounts]. A delete that succeeded is followed by the wipe of [keys] and
 * then of [chatData], the keys first since they are the secrets.
 */
internal class FirebaseAccountRepository(
    private val auth: AuthManager,
    private val accounts: AccountManager,
    private val keys: ProviderKeyRepository,
    private val chatData: ChatDataEraser,
) : AccountRepository {
    override fun observeAccount(): Flow<Account?> =
        combine(auth.currentUser, accounts.signInMethods) { user, methods -> user?.let { Account(it, methods) } }
            .distinctUntilChanged()

    override fun signOut() = auth.signOut()

    /** Non-cancellable, so that the caller going away can neither stop a delete nor the wipe after it. */
    override suspend fun deleteAccount() {
        withContext(NonCancellable) {
            accounts.deleteAccount()
            keys.removeAll()
            chatData.eraseAll()
        }
    }
}
