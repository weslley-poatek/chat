package br.com.weslleycampos.chat.core.ui.components

import androidx.compose.ui.graphics.Color
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatSegmentedControlTest {
    @Test
    fun selectedSegmentInvertsTheSurfaceInEveryPalette() {
        for (palette in ChatPalette.entries) {
            for (dark in listOf(false, true)) {
                val colors = palette.colors(dark)
                assertEquals(
                    colors.text.primary to colors.surface.primary,
                    chatSegmentColors(colors = colors, selected = true),
                )
                assertEquals(
                    Color.Transparent to colors.text.secondary,
                    chatSegmentColors(colors = colors, selected = false),
                )
            }
        }
    }

    @Test
    fun providerCoversEverySegmentStateInBothModes() {
        val cases = ChatSegmentPreviewParameterProvider().values.toList()
        assertEquals(20, cases.size)
        assertEquals(20, cases.distinct().size)
        for (selected in listOf(false, true)) {
            for (dark in listOf(false, true)) {
                assertEquals(
                    ChatSegmentPreviewState.entries.toSet(),
                    cases.filter { it.selected == selected && it.darkTheme == dark }.map { it.state }.toSet(),
                )
            }
        }
    }
}
