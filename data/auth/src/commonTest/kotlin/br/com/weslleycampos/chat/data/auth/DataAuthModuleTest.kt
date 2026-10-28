package br.com.weslleycampos.chat.data.auth

import org.koin.dsl.koinApplication
import org.koin.plugin.module.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/** Resolving only builds the platform classes; neither touches Firebase until it is called. */
class DataAuthModuleTest {
    @Test
    fun koinProvidesOneEmailAuthManagerNextToTheAuthManager() {
        val koin = koinApplication { module<DataAuthModule>() }.koin
        try {
            assertSame(koin.get<EmailAuthManager>(), koin.get<EmailAuthManager>())
            assertNotNull(koin.getOrNull<AuthManager>())
        } finally {
            koin.close()
        }
    }

    @Test
    fun koinProvidesOneAccountManagerNextToTheAuthManager() {
        val koin = koinApplication { module<DataAuthModule>() }.koin
        try {
            assertSame(koin.get<AccountManager>(), koin.get<AccountManager>())
            assertNotNull(koin.getOrNull<AuthManager>())
        } finally {
            koin.close()
        }
    }
}
