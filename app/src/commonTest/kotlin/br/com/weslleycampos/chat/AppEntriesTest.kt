package br.com.weslleycampos.chat

import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.entries.auth.SignInEntry
import br.com.weslleycampos.chat.core.navigation.entries.auth.SplashEntry
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.utils.EntriesAggregator
import br.com.weslleycampos.chat.core.navigation.utils.providesSaveStateConfiguration
import br.com.weslleycampos.chat.feature.auth.signin.SignInEntryProvider
import br.com.weslleycampos.chat.feature.auth.splash.SplashEntryProvider
import br.com.weslleycampos.chat.feature.chat.ChatEntryProvider
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every key the app navigates to survives the back stack's save: on iOS it is saved
 * polymorphically, and a key no provider registers throws once the app goes to the background.
 */
class AppEntriesTest {
    private val configuration = providesSaveStateConfiguration(
        EntriesAggregator(listOf(SplashEntryProvider(), SignInEntryProvider(), ChatEntryProvider())),
    )
    private val json = Json { serializersModule = configuration.serializersModule }
    private val serializer = ListSerializer(PolymorphicSerializer(NavKey::class))

    @Test
    fun theSavedBackStackRestoresEveryScreenTheAppShows() {
        val backStack = listOf(SplashEntry, SignInEntry, ChatEntry(), ChatEntry(conversationId = 42))

        assertEquals(backStack, json.decodeFromString(serializer, json.encodeToString(serializer, backStack)))
    }
}
