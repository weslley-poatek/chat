package br.com.weslleycampos.chat.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AppVersionIosTest {
    @Test
    fun theVersionIsTheBundlesShortVersionStringAndBuildNumberOnIos() {
        val info = mapOf("CFBundleShortVersionString" to "1.0", "CFBundleVersion" to "1")

        assertEquals(AppVersion(name = "1.0", code = "1", platform = "ios"), appVersion { info[it] })
        assertEquals("Chat 1.0 (1) · ios", settingsFooter(appVersion { info[it] }))
    }

    @Test
    fun aBundleWithoutTheKeysReadsAsNoVersion() {
        assertEquals(AppVersion(name = "", code = "", platform = "ios"), appVersion { null })
        assertEquals("Chat · ios", settingsFooter(appVersion { null }))
    }
}
