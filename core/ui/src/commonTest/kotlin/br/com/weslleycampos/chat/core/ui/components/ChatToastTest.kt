package br.com.weslleycampos.chat.core.ui.components

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class ChatToastTest {
    @Test
    fun theToastInvertsTheScreenColorsInEveryPalette() {
        for (palette in ChatPalette.entries) {
            for (dark in listOf(false, true)) {
                val colors = palette.colors(dark)
                assertEquals(colors.text.primary to colors.surface.primary, chatToastColors(colors))
            }
        }
    }

    @Test
    fun aToastShowsForTheDesignsFourteenHundredMilliseconds() {
        assertEquals(1400.milliseconds, ChatToastDefaults.Duration)
    }

    @Test
    fun providerCoversHiddenShortAndLongToastsInBothModes() {
        val cases = ChatToastPreviewParameterProvider().values.toList()
        assertEquals(6, cases.size)
        assertEquals(6, cases.distinct().size)
        for (dark in listOf(false, true)) {
            assertEquals(
                ChatToastPreviewState.entries.toSet(),
                cases.filter { it.darkTheme == dark }.map { it.state }.toSet(),
            )
        }
    }
}
