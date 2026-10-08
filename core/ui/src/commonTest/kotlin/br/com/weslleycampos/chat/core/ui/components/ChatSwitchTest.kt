package br.com.weslleycampos.chat.core.ui.components

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatSwitchTest {
    @Test
    fun trackFollowsTheAccentOnlyWhenCheckedInEveryPalette() {
        for (palette in ChatPalette.entries) {
            for (dark in listOf(false, true)) {
                val colors = palette.colors(dark)
                assertEquals(
                    colors.accent.primary to colors.surface.knob,
                    chatSwitchColors(colors = colors, checked = true),
                )
                assertEquals(
                    colors.surface.tertiary to colors.surface.knob,
                    chatSwitchColors(colors = colors, checked = false),
                )
            }
        }
    }

    @Test
    fun providerCoversEverySwitchStateInBothModes() {
        val cases = ChatSwitchPreviewParameterProvider().values.toList()
        assertEquals(20, cases.size)
        assertEquals(20, cases.distinct().size)
        for (checked in listOf(false, true)) {
            for (dark in listOf(false, true)) {
                assertEquals(
                    ChatSwitchPreviewState.entries.toSet(),
                    cases.filter { it.checked == checked && it.darkTheme == dark }.map { it.state }.toSet(),
                )
            }
        }
    }
}
