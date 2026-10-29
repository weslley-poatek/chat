package br.com.weslleycampos.chat.data.settings

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import br.com.weslleycampos.chat.data.settings.fakes.FakePreferences
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Every key here is an obvious fake. */
class EncryptedFuelixKeySourceTest {
    private val fuelixKey = "sk-test-0000000000000000000000000000"
    private val otherFuelixKey = "sk-test-0000000000000000000000001111"

    private val preferences = FakePreferences()
    private val repository = EncryptedProviderKeyRepository(preferences)
    private val source = EncryptedFuelixKeySource(preferences)

    @Test
    fun theKeyIsNullWhileNoneIsStoredAndReadingItWritesNothing() = runTest {
        assertNull(source.key())
        assertEquals(0, preferences.writes)
    }

    @Test
    fun theKeyIsTheWholeOneSettingsSavedTrimmed() = runTest {
        repository.save(ModelProvider.FUELIX, "  $fuelixKey\n")

        assertEquals(fuelixKey, source.key())
    }

    @Test
    fun eachReadIsTheKeyStoredThenSoASavedOrRemovedKeyCountsFromTheNextRead() = runTest {
        repository.save(ModelProvider.FUELIX, fuelixKey)
        assertEquals(fuelixKey, source.key())

        repository.save(ModelProvider.FUELIX, otherFuelixKey)
        assertEquals(otherFuelixKey, source.key())

        repository.remove(ModelProvider.FUELIX)
        assertNull(source.key())

        repository.save(ModelProvider.FUELIX, fuelixKey)
        repository.removeAll()
        assertNull(source.key())
    }

    @Test
    fun aKeySavedForAProviderNoLongerListedIsNeverRead() = runTest {
        preferences.stored.value = preferencesOf(
            stringPreferencesKey("provider_key_openai") to "sk-test-openai-000000000000000000",
            stringPreferencesKey("not_a_provider_key") to "kept",
        )

        assertNull(source.key())
    }
}
