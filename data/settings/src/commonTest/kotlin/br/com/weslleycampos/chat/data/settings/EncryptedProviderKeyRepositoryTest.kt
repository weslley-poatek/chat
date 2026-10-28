package br.com.weslleycampos.chat.data.settings

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import br.com.weslleycampos.chat.data.settings.EncryptedProviderKeyRepository.Companion.keyOf
import br.com.weslleycampos.chat.data.settings.fakes.FakePreferences
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.data.settings.models.ProviderKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every key here is an obvious fake. */
@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedProviderKeyRepositoryTest {
    private val anthropicKey = "sk-ant-test-0000000000000000000000000000"
    private val openAiKey = "sk-test-0000000000000000000000001111"

    private val preferences = FakePreferences()
    private val repository = EncryptedProviderKeyRepository(preferences)

    private suspend fun masked(): List<String?> = repository.observeKeys().first().map(ProviderKey::masked)

    @Test
    fun everyProviderIsListedInThePrototypesOrderAndNoneHasAKeyOnAFreshInstall() = runTest {
        val keys = repository.observeKeys().first()

        assertEquals(ModelProvider.entries, keys.map(ProviderKey::provider))
        assertTrue(keys.none(ProviderKey::hasKey))
        assertTrue(keys.all { it.masked == null })
    }

    @Test
    fun aSavedKeyIsStoredTrimmedAndReadBackOnlyMasked() = runTest {
        assertTrue(repository.save(ModelProvider.ANTHROPIC, "  $anthropicKey\n"))

        val keys = repository.observeKeys().first()
        assertEquals(ProviderKey(ModelProvider.ANTHROPIC, "sk-ant-…0000"), keys.first())
        assertTrue(keys.first().hasKey)
        assertTrue(keys.drop(1).none(ProviderKey::hasKey))
        assertEquals(anthropicKey, preferences.stored.value[keyOf(ModelProvider.ANTHROPIC)])
        assertTrue(keys.none { it.toString().contains(anthropicKey) })
    }

    @Test
    fun aShortKeyIsMaskedToLessThanHalfOfIt() = runTest {
        assertTrue(repository.save(ModelProvider.MISTRAL, "sk-test-0000000000"))
        assertTrue(repository.save(ModelProvider.META, "fake567"))

        assertEquals(listOf(null, null, null, "…", null, "…0000"), masked())
    }

    @Test
    fun savingABlankKeyStoresNothingAndKeepsTheKeyTheProviderHad() = runTest {
        assertFalse(repository.save(ModelProvider.OPENAI, ""))
        assertFalse(repository.save(ModelProvider.OPENAI, "   \n\t"))
        assertEquals(0, preferences.writes)
        assertNull(masked()[1])

        assertTrue(repository.save(ModelProvider.OPENAI, openAiKey))
        assertFalse(repository.save(ModelProvider.OPENAI, " "))

        assertEquals("sk-test…1111", masked()[1])
        assertEquals(openAiKey, preferences.stored.value[keyOf(ModelProvider.OPENAI)])
    }

    @Test
    fun savingAgainReplacesTheProvidersKey() = runTest {
        repository.save(ModelProvider.GOOGLE, "AIza-test-00000000000000000000000")
        assertTrue(repository.save(ModelProvider.GOOGLE, "AIza-test-11111111111111111119999"))

        assertEquals("AIza-te…9999", masked()[2])
    }

    @Test
    fun removingAKeyRemovesOnlyThatProvidersAndSaysWhetherItHadOne() = runTest {
        repository.save(ModelProvider.ANTHROPIC, anthropicKey)
        repository.save(ModelProvider.OPENAI, openAiKey)

        assertTrue(repository.remove(ModelProvider.ANTHROPIC))
        assertFalse(repository.remove(ModelProvider.ANTHROPIC))
        assertFalse(repository.remove(ModelProvider.DEEPSEEK))

        assertEquals(listOf(null, "sk-test…1111", null, null, null, null), masked())
        assertNull(preferences.stored.value[keyOf(ModelProvider.ANTHROPIC)])
    }

    @Test
    fun removingEveryKeyLeavesNoneAndLeavesOtherPreferencesAlone() = runTest {
        val other = stringPreferencesKey("not_a_provider_key")
        preferences.stored.value = preferencesOf(other to "kept")
        ModelProvider.entries.forEach { repository.save(it, "sk-test-${it.id}-000000000000000000") }

        repository.removeAll()

        assertTrue(repository.observeKeys().first().none(ProviderKey::hasKey))
        assertEquals(preferencesOf(other to "kept"), preferences.stored.value)
        val writes = preferences.writes
        repository.removeAll()
        assertEquals(writes, preferences.writes)
    }

    @Test
    fun theKeysEmitOnCollectAndOnlyWhenARowChanges() = runTest {
        val emissions = mutableListOf<List<String?>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeKeys().collect { keys -> emissions += keys.map(ProviderKey::masked) }
        }

        repository.save(ModelProvider.ANTHROPIC, anthropicKey)
        repository.save(ModelProvider.ANTHROPIC, anthropicKey)
        repository.save(ModelProvider.ANTHROPIC, " ")
        repository.remove(ModelProvider.OPENAI)
        repository.remove(ModelProvider.ANTHROPIC)

        assertEquals(
            listOf(
                List(6) { null },
                listOf("sk-ant-…0000", null, null, null, null, null),
                List(6) { null },
            ),
            emissions,
        )
    }
}
