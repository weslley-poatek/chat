package br.com.weslleycampos.chat.data.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Android SDK's provider ids are the ones [SignInMethod] maps. */
class FirebaseSignInMethodTest {
    @Test
    fun mapsTheAndroidSdksProviderIds() {
        assertEquals(
            listOf(SignInMethod.GOOGLE, SignInMethod.EMAIL_PASSWORD),
            SignInMethod.of(listOf(EmailAuthProvider.PROVIDER_ID, GoogleAuthProvider.PROVIDER_ID)),
        )
    }
}
