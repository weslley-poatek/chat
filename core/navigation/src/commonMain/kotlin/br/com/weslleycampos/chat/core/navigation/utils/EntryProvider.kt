package br.com.weslleycampos.chat.core.navigation.utils

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.SerializersModule

interface EntryProvider {
    fun serializerModule(): SerializersModule
    fun entryBuilder(): EntryProviderScope<NavKey>.() -> Unit
}
