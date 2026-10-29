package br.com.weslleycampos.chat.data.settings

import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.SignInMethod
import br.com.weslleycampos.chat.data.settings.fakes.ANA
import br.com.weslleycampos.chat.data.settings.fakes.FakeAccountManager
import br.com.weslleycampos.chat.data.settings.fakes.FakeAuthManager
import br.com.weslleycampos.chat.data.settings.fakes.FakeChatDataEraser
import br.com.weslleycampos.chat.data.settings.fakes.FakePreferences
import br.com.weslleycampos.chat.data.settings.models.Account
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.data.settings.models.ProviderKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every key here is an obvious fake. */
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAccountRepositoryTest {
    private val events = mutableListOf<String>()
    private val auth = FakeAuthManager()
    private val accounts = FakeAccountManager(auth = auth, events = events)
    private val preferences = FakePreferences()
    private val keys = EncryptedProviderKeyRepository(preferences)

    /** The real key repository, noting when every key is removed. */
    private val recordedKeys = object : ProviderKeyRepository by keys {
        override suspend fun removeAll() {
            events += "remove keys"
            keys.removeAll()
        }
    }

    private val repository = FirebaseAccountRepository(
        auth = auth,
        accounts = accounts,
        keys = recordedKeys,
        chatData = FakeChatDataEraser(events),
    )

    private suspend fun storedKeys(): List<ModelProvider> =
        keys.observeKeys().first().filter(ProviderKey::hasKey).map(ProviderKey::provider)

    @Test
    fun theAccountIsTheSignedInUserWithHowTheySignInAndNullWhileSignedOut() = runTest {
        val emissions = mutableListOf<Account?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeAccount().collect { emissions += it }
        }

        accounts.methods.value = listOf(SignInMethod.GOOGLE, SignInMethod.EMAIL_PASSWORD)
        accounts.methods.value = listOf(SignInMethod.GOOGLE, SignInMethod.EMAIL_PASSWORD)
        repository.signOut()

        assertEquals(
            listOf(
                Account(ANA, listOf(SignInMethod.GOOGLE)),
                Account(ANA, listOf(SignInMethod.GOOGLE, SignInMethod.EMAIL_PASSWORD)),
                null,
            ),
            emissions,
        )
    }

    @Test
    fun signingOutKeepsEveryKeyAndTheChatsData() = runTest {
        keys.save(ModelProvider.FUELIX, "sk-test-0000000000000000000000000000")

        repository.signOut()

        assertEquals(1, auth.signOuts)
        assertNull(repository.observeAccount().first())
        assertEquals(listOf(ModelProvider.FUELIX), storedKeys())
        assertTrue(events.isEmpty())
    }

    @Test
    fun deletingTheAccountDeletesItOnFirebaseFirstThenWipesTheKeysAndTheChatsData() = runTest {
        keys.save(ModelProvider.FUELIX, "sk-test-0000000000000000000000000000")

        repository.deleteAccount()

        assertEquals(listOf("delete", "deleted", "remove keys", "erase chat"), events)
        assertTrue(storedKeys().isEmpty())
        assertNull(repository.observeAccount().first())
    }

    @Test
    fun aDeleteFirebaseRefusesThrowsItsAuthExceptionAndWipesNothing() = runTest {
        keys.save(ModelProvider.FUELIX, "sk-test-0000000000000000000000000000")
        val refusals = listOf(
            AuthException.RequiresRecentLogin("sign in again", null),
            AuthException.NotSignedIn("nobody", null),
            AuthException.NetworkError("offline", null),
            AuthException.Unknown("boom", null),
        )

        for (refusal in refusals) {
            accounts.failure = refusal

            val thrown = assertFailsWith<AuthException> { repository.deleteAccount() }

            assertEquals(refusal::class, thrown::class)
            assertEquals(refusal.message, thrown.message)
        }

        assertEquals(List(refusals.size) { "delete" }, events)
        assertEquals(listOf(ModelProvider.FUELIX), storedKeys())
        assertEquals(Account(ANA, listOf(SignInMethod.GOOGLE)), repository.observeAccount().first())
    }

    @Test
    fun aCancellationFromFirebaseIsRethrownUntouchedAndWipesNothing() = runTest {
        keys.save(ModelProvider.FUELIX, "sk-test-0000000000000000000000000000")
        val cancellation = CancellationException("task cancelled")
        accounts.failure = cancellation

        val thrown = assertFailsWith<CancellationException> { repository.deleteAccount() }

        assertEquals("task cancelled", thrown.message)

        assertEquals(listOf("delete"), events)
        assertEquals(listOf(ModelProvider.FUELIX), storedKeys())
    }

    @Test
    fun cancellingTheCallerWhileFirebaseDeletesStillWipesTheDeviceOnceTheDeleteSucceeds() = runTest {
        keys.save(ModelProvider.FUELIX, "sk-test-0000000000000000000000000000")
        val answer = CompletableDeferred<Unit>()
        accounts.answer = answer
        val call = launch(start = CoroutineStart.UNDISPATCHED) { repository.deleteAccount() }

        call.cancel()
        assertEquals(listOf("delete"), events)
        answer.complete(Unit)
        advanceUntilIdle()

        assertTrue(call.isCancelled)
        assertEquals(listOf("delete", "deleted", "remove keys", "erase chat"), events)
        assertTrue(storedKeys().isEmpty())
    }
}
