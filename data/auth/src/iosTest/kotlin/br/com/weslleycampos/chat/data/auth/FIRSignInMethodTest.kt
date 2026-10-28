@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.data.auth

import kotlinx.cinterop.ExperimentalForeignApi
import swiftPMImport.Chat.data.data.auth.FIREmailAuthProviderID
import swiftPMImport.Chat.data.data.auth.FIRGoogleAuthProviderID
import kotlin.test.Test
import kotlin.test.assertEquals

/** The iOS SDK's provider ids are the ones [SignInMethod] maps. */
class FIRSignInMethodTest {
    @Test
    fun mapsTheIosSdksProviderIds() {
        assertEquals(
            listOf(SignInMethod.GOOGLE, SignInMethod.EMAIL_PASSWORD),
            SignInMethod.of(listOf(FIREmailAuthProviderID, FIRGoogleAuthProviderID)),
        )
    }
}
