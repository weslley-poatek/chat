package br.com.weslleycampos.chat.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import br.com.weslleycampos.chat.core.preferences.qualifiers.EncryptedPreferences
import br.com.weslleycampos.chat.data.auth.AccountManager
import br.com.weslleycampos.chat.data.auth.AuthManager
import br.com.weslleycampos.chat.data.chat.ChatDataEraser
import br.com.weslleycampos.chat.data.fuelix.FuelixKeySource
import br.com.weslleycampos.chat.data.settings.fakes.FakeAccountManager
import br.com.weslleycampos.chat.data.settings.fakes.FakeAuthManager
import br.com.weslleycampos.chat.data.settings.fakes.FakeChatDataEraser
import br.com.weslleycampos.chat.data.settings.fakes.FakePreferences
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import kotlinx.coroutines.test.runTest
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.koinApplication
import org.koin.plugin.module.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * `DataSettingsModule` next to fakes of what `PreferencesModule`, `DataAuthModule` and
 * `DataChatModule` provide in the app, so the Koin compiler plugin checks the graph while this
 * compiles and neither Firebase nor a DataStore file is touched.
 */
class DataSettingsModuleTest {
    @Test
    fun koinProvidesOneEncryptedProviderKeyRepositoryOnTheEncryptedPreferences() {
        val koin = koinApplication {
            module<FakeSettingsDependencies>()
            module<DataSettingsModule>()
        }.koin
        try {
            assertIs<EncryptedProviderKeyRepository>(koin.get<ProviderKeyRepository>())
            assertSame(koin.get<ProviderKeyRepository>(), koin.get<ProviderKeyRepository>())
        } finally {
            koin.close()
        }
    }

    @Test
    fun koinProvidesOneEncryptedFuelixKeySourceThatReadsTheKeyTheRepositorySaves() = runTest {
        val koin = koinApplication {
            module<FakeSettingsDependencies>()
            module<DataSettingsModule>()
        }.koin
        try {
            assertIs<EncryptedFuelixKeySource>(koin.get<FuelixKeySource>())
            assertSame(koin.get<FuelixKeySource>(), koin.get<FuelixKeySource>())

            koin.get<ProviderKeyRepository>().save(ModelProvider.FUELIX, "sk-test-0000000000000000000000000000")

            assertEquals("sk-test-0000000000000000000000000000", koin.get<FuelixKeySource>().key())
        } finally {
            koin.close()
        }
    }

    @Test
    fun koinProvidesOneFirebaseAccountRepositoryOnTheAuthManagersAndTheChatsEraser() {
        val koin = koinApplication {
            module<FakeSettingsDependencies>()
            module<DataSettingsModule>()
        }.koin
        try {
            assertIs<FirebaseAccountRepository>(koin.get<AccountRepository>())
            assertSame(koin.get<AccountRepository>(), koin.get<AccountRepository>())
        } finally {
            koin.close()
        }
    }
}

@Module
internal class FakeSettingsDependencies {
    @Single
    fun authManager(): AuthManager = FakeAuthManager()

    @Single
    fun accountManager(): AccountManager = FakeAccountManager(auth = FakeAuthManager(), events = mutableListOf())

    @Single
    fun chatDataEraser(): ChatDataEraser = FakeChatDataEraser(mutableListOf())

    @Single
    @EncryptedPreferences
    fun preferences(): DataStore<Preferences> = FakePreferences()
}
