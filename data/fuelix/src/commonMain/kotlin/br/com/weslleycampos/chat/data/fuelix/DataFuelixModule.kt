package br.com.weslleycampos.chat.data.fuelix

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Fuelix's API: [FuelixClient], on the [FuelixKeySource] that `DataSettingsModule` provides, which
 * the app's root module includes next to this one.
 */
@Module
@ComponentScan
class DataFuelixModule
