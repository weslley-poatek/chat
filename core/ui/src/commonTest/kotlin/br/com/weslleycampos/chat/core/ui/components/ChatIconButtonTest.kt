package br.com.weslleycampos.chat.core.ui.components

import androidx.compose.ui.graphics.Color
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatIconButtonTest {
    @Test
    fun interactionColorsFollowTheVariantInEveryPalette() {
        for (palette in ChatPalette.entries) {
            for (dark in listOf(false, true)) {
                val colors = palette.colors(dark)
                for (variant in ChatIconButtonVariant.entries) {
                    assertEquals(
                        chatIconButtonColors(colors = colors, variant = variant, enabled = true),
                        chatIconButtonColors(
                            colors = colors,
                            variant = variant,
                            enabled = false,
                            hovered = true,
                            pressed = true,
                        ),
                    )
                }
                assertEquals(
                    Color.Transparent to colors.text.secondary,
                    chatIconButtonColors(colors = colors, variant = ChatIconButtonVariant.Standard, enabled = true),
                )
                for (interaction in listOf(true to false, false to true)) {
                    assertEquals(
                        colors.surface.secondary to colors.text.secondary,
                        chatIconButtonColors(
                            colors = colors,
                            variant = ChatIconButtonVariant.Standard,
                            enabled = true,
                            hovered = interaction.first,
                            pressed = interaction.second,
                        ),
                    )
                }
                assertEquals(
                    colors.accent.primary to colors.text.onAccent,
                    chatIconButtonColors(
                        colors = colors,
                        variant = ChatIconButtonVariant.Accent,
                        enabled = true,
                        pressed = true,
                    ),
                )
            }
        }
    }

    @Test
    fun providerCoversAllVariantsAndStatesInBothModes() {
        val cases = ChatIconButtonPreviewParameterProvider().values.toList()
        assertEquals(20, cases.size)
        assertEquals(20, cases.distinct().size)
        for (variant in ChatIconButtonVariant.entries) {
            for (dark in listOf(false, true)) {
                assertEquals(
                    ChatIconButtonPreviewState.entries.toSet(),
                    cases.filter { it.variant == variant && it.darkTheme == dark }.map { it.state }.toSet(),
                )
            }
        }
    }
}
