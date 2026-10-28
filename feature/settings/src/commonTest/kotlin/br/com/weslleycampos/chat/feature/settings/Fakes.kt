package br.com.weslleycampos.chat.feature.settings

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.data.auth.AuthUser
import br.com.weslleycampos.chat.data.auth.SignInMethod
import br.com.weslleycampos.chat.data.settings.AccountRepository
import br.com.weslleycampos.chat.data.settings.ProviderKeyRepository
import br.com.weslleycampos.chat.data.settings.models.Account
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.data.settings.models.ProviderKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Obvious fakes: no test ever holds a real key. */
internal const val FakeKey = "sk-test-0000000000"
internal const val OtherFakeKey = "sk-test-1111111111"

/** The prototype's profile, signed in with Google. */
internal val Ana = Account(
    user = AuthUser(id = "ana", displayName = "Ana Ribeiro", email = "ana.ribeiro@example.dev", photoUrl = null),
    signInMethods = listOf(SignInMethod.GOOGLE),
)

/**
 * An in-memory [AccountRepository] with the real one's contract: signing out and a delete that
 * succeeds both end with `null`; a delete runs [delete] first, which by default succeeds and
 * whose throwing keeps the user signed in.
 */
internal class FakeAccountRepository(account: Account? = Ana) : AccountRepository {
    val account = MutableStateFlow(account)
    var signOuts = 0
    var deletes = 0

    /** How Firebase answers a delete; throw to refuse it. */
    var delete: suspend () -> Unit = {}

    override fun observeAccount(): Flow<Account?> = account

    override fun signOut() {
        signOuts++
        account.value = null
    }

    override suspend fun deleteAccount() {
        deletes++
        delete()
        // Firebase signs the user out once the account is deleted, before the wipe returns.
        account.value = null
    }
}

/**
 * An in-memory [ProviderKeyRepository] with the real one's contract: six rows in [ModelProvider]
 * order, a blank save that stores nothing and returns `false`, and keys that leave it only masked
 * (as "…" and the last 4, enough to tell two fakes apart).
 */
internal class FakeProviderKeyRepository(keys: Map<ModelProvider, String> = emptyMap()) : ProviderKeyRepository {
    val stored = MutableStateFlow(keys)

    /** Every `save`, as (provider, key as typed), in order. */
    val saves = mutableListOf<Pair<ModelProvider, String>>()

    /** Every `remove`, in order. */
    val removes = mutableListOf<ModelProvider>()

    /** Set to make every write fail, as an encrypted store that cannot be written would. */
    var failure: Exception? = null

    override fun observeKeys(): Flow<List<ProviderKey>> = stored.map { all ->
        ModelProvider.entries.map { ProviderKey(provider = it, masked = all[it]?.let(::fakeMask)) }
    }

    override suspend fun save(provider: ModelProvider, key: String): Boolean {
        saves += provider to key
        failure?.let { throw it }
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return false
        stored.update { it + (provider to trimmed) }
        return true
    }

    override suspend fun remove(provider: ModelProvider): Boolean {
        removes += provider
        failure?.let { throw it }
        val had = provider in stored.value
        stored.update { it - provider }
        return had
    }

    override suspend fun removeAll() {
        stored.value = emptyMap()
    }
}

internal fun fakeMask(key: String): String = "…" + key.takeLast(4)

internal fun navigator(vararg keys: NavKey) = NavigatorImpl(NavBackStack<NavKey>().apply { addAll(keys) })

internal val NavigatorImpl.stack: List<NavKey> get() = navBackStack.toList()
