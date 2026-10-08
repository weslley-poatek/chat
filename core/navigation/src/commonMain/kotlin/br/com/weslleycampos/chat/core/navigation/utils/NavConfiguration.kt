package br.com.weslleycampos.chat.core.navigation.utils

import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import org.koin.core.annotation.Single

@Single
fun providesSaveStateConfiguration(entriesAggregator: EntriesAggregator) = SavedStateConfiguration {
    serializersModule = SerializersModule {
        entriesAggregator.entries.forEach { entry -> include(entry.serializerModule()) }
    }
}
