package br.com.weslleycampos.chat.core.ui.components

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatTextFieldTest {
    @Test
    fun onlyAnEnabledFocusedFieldHighlightsItsBorderInEveryPalette() {
        for (palette in ChatPalette.entries) {
            for (dark in listOf(false, true)) {
                val colors = palette.colors(dark)
                val resting = chatTextFieldColors(colors = colors, enabled = true)
                assertEquals(
                    ChatTextFieldColors(
                        container = colors.surface.primary,
                        border = colors.border.primary,
                        text = colors.text.primary,
                        placeholder = colors.text.tertiary,
                        selection = colors.accent.primary,
                    ),
                    resting,
                )
                assertEquals(
                    resting.copy(border = colors.text.secondary),
                    chatTextFieldColors(colors = colors, enabled = true, focused = true),
                )
                assertEquals(resting, chatTextFieldColors(colors = colors, enabled = false, focused = true))
            }
        }
    }

    @Test
    fun providerCoversEveryFieldStateInBothModes() {
        val fields = ChatTextFieldPreviewParameterProvider().values.toList()
        assertEquals(14, fields.size)
        assertEquals(14, fields.distinct().size)
        for (dark in listOf(false, true)) {
            assertEquals(
                ChatTextFieldPreviewState.entries.toSet(),
                fields.filter { it.darkTheme == dark }.map { it.state }.toSet(),
            )
        }
    }
}
