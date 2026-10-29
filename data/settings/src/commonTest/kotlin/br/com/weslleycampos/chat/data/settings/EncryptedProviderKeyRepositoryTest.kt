package br.com.weslleycampos.chat.data.settings

import androidx.datastore.preferences.core.edit
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
    private val fuelixKey = "sk-test-0000000000000000000000000000"
    private val otherFuelixKey = "sk-test-0000000000000000000000001111"

    /** A preference that is not a provider key. */
    private val other = stringPreferencesKey("not_a_provider_key")

    /** Keys the builds before Fuelix stored, under their providers' ids. */
    private val retiredKeys = listOf("anthropic", "openai", "google", "meta", "deepseek", "mistral")
        .map { stringPreferencesKey("provider_key_$it") to "sk-test-$it-000000000000000000" }

    private val preferences = FakePreferences()
    private val repository = EncryptedProviderKeyRepository(preferences)

    private suspend fun masked(): String? = repository.observeKeys().first().single().masked

    @Test
    fun fuelixIsTheOnlyProviderAndHasNoKeyOnAFreshInstall() = runTest {
        assertEquals(listOf(ProviderKey(ModelProvider.FUELIX, null)), repository.observeKeys().first())
    }

    @Test
    fun aSavedKeyIsStoredTrimmedAndReadBackOnlyMasked() = runTest {
        assertTrue(repository.save(ModelProvider.FUELIX, "  $fuelixKey\n"))

        val keys = repository.observeKeys().first()
        assertEquals(listOf(ProviderKey(ModelProvider.FUELIX, "sk-test…0000")), keys)
        assertTrue(keys.single().hasKey)
        assertEquals(fuelixKey, preferences.stored.value[keyOf(ModelProvider.FUELIX)])
        assertTrue(keys.none { it.toString().contains(fuelixKey) })
    }

    @Test
    fun aShortKeyIsMaskedToLessThanHalfOfIt() = runTest {
        assertTrue(repository.save(ModelProvider.FUELIX, "sk-test-0000000000"))
        assertEquals("…0000", masked())

        assertTrue(repository.save(ModelProvider.FUELIX, "fake567"))
        assertEquals("…", masked())
    }

    @Test
    fun savingABlankKeyStoresNothingAndKeepsTheKeyTheProviderHad() = runTest {
        assertFalse(repository.save(ModelProvider.FUELIX, ""))
        assertFalse(repository.save(ModelProvider.FUELIX, "   \n\t"))
        assertEquals(0, preferences.writes)
        assertNull(masked())

        assertTrue(repository.save(ModelProvider.FUELIX, otherFuelixKey))
        assertFalse(repository.save(ModelProvider.FUELIX, " "))

        assertEquals("sk-test…1111", masked())
        assertEquals(otherFuelixKey, preferences.stored.value[keyOf(ModelProvider.FUELIX)])
    }

    @Test
    fun savingAgainReplacesTheProvidersKey() = runTest {
        repository.save(ModelProvider.FUELIX, fuelixKey)
        assertTrue(repository.save(ModelProvider.FUELIX, otherFuelixKey))

        assertEquals("sk-test…1111", masked())
    }

    @Test
    fun removingTheKeySaysWhetherItHadOneAndLeavesOtherPreferencesAlone() = runTest {
        preferences.stored.value = preferencesOf(other to "kept")
        repository.save(ModelProvider.FUELIX, fuelixKey)

        assertTrue(repository.remove(ModelProvider.FUELIX))
        assertFalse(repository.remove(ModelProvider.FUELIX))

        assertNull(masked())
        assertEquals(preferencesOf(other to "kept"), preferences.stored.value)
    }

    @Test
    fun aKeySavedForAProviderNoLongerListedIsNeverRead() = runTest {
        preferences.stored.value = preferencesOf(*retiredKeys.toTypedArray())

        assertEquals(listOf(ProviderKey(ModelProvider.FUELIX, null)), repository.observeKeys().first())
    }

    @Test
    fun removingEveryKeyRemovesThoseOfProvidersNoLongerListedTooAndLeavesOtherPreferencesAlone() = runTest {
        preferences.stored.value = preferencesOf(other to "kept", *retiredKeys.toTypedArray())
        repository.save(ModelProvider.FUELIX, fuelixKey)

        repository.removeAll()

        assertNull(masked())
        assertEquals(preferencesOf(other to "kept"), preferences.stored.value)
        val writes = preferences.writes
        repository.removeAll()
        assertEquals(writes, preferences.writes)
    }

    @Test
    fun theKeysEmitOnCollectAndOnlyWhenARowChanges() = runTest {
        val emissions = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeKeys().collect { keys -> emissions += keys.single().masked }
        }

        repository.save(ModelProvider.FUELIX, fuelixKey)
        repository.save(ModelProvider.FUELIX, fuelixKey)
        repository.save(ModelProvider.FUELIX, " ")
        preferences.edit { it[other] = "changed" }
        repository.remove(ModelProvider.FUELIX)
        repository.remove(ModelProvider.FUELIX)

        assertEquals(listOf(null, "sk-test…0000", null), emissions)
    }
}
