package br.com.weslleycampos.chat.core.navigation.entries.auth

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SignInEntryTest {
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) { subclass(SignInEntry::class, SignInEntry.serializer()) }
        }
    }

    @Test
    fun signInIsTheDefaultMode() {
        assertFalse(SignInEntry().signUp)
    }

    @Test
    fun aSavedBackStackRestoresSignInAndSignUp() {
        for (entry in listOf(SignInEntry(), SignInEntry(signUp = true))) {
            val saved = json.encodeToString(PolymorphicSerializer(NavKey::class), entry)

            assertEquals<NavKey>(entry, json.decodeFromString(PolymorphicSerializer(NavKey::class), saved))
        }
    }
}
