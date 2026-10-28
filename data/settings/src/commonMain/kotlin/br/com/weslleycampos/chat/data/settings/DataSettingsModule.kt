package br.com.weslleycampos.chat.data.settings

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Settings' data: [ProviderKeyRepository] on the encrypted DataStore of `PreferencesModule`, and
 * [AccountRepository] on `DataAuthModule`'s managers and `DataChatModule`'s eraser, which the app's
 * root module includes next to this one.
 */
@Module
@ComponentScan
class DataSettingsModule
