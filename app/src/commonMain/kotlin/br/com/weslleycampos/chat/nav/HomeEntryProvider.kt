package br.com.weslleycampos.chat.nav

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.HomeScreen
import br.com.weslleycampos.chat.core.navigation.entries.home.HomeEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntryProvider
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.core.annotation.Single

@Single
class HomeEntryProvider : EntryProvider {
    override fun serializerModule() = SerializersModule {
        polymorphic(NavKey::class) { subclass(HomeEntry::class, HomeEntry.serializer()) }
    }

    override fun entryBuilder(): EntryProviderScope<NavKey>.() -> Unit = {
        entry<HomeEntry> { HomeScreen() }
    }
}
