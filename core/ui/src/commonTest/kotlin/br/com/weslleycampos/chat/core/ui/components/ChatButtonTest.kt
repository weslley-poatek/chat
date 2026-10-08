package br.com.weslleycampos.chat.core.ui.components

import androidx.compose.ui.graphics.Color
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatButtonTest {
    @Test
    fun interactionColorsFollowTheVariantInEveryPalette() {
        for (palette in ChatPalette.entries) {
            for (dark in listOf(false, true)) {
                val colors = palette.colors(dark)
                for (variant in ChatButtonVariant.entries) {
                    assertEquals(
                        chatButtonColors(colors = colors, variant = variant, enabled = true),
                        chatButtonColors(
                            colors = colors,
                            variant = variant,
                            enabled = false,
                            hovered = true,
                            pressed = true,
                        ),
                    )
                    assertEquals(
                        chatButtonColors(colors = colors, variant = variant, enabled = true, hovered = true),
                        chatButtonColors(colors = colors, variant = variant, enabled = true, pressed = true),
                    )
                }
                val primary = chatButtonColors(
                    colors = colors,
                    variant = ChatButtonVariant.Primary,
                    enabled = true,
                    hovered = true,
                )
                assertEquals(
                    ChatButtonColors(
                        container = colors.text.primary,
                        content = colors.surface.primary,
                        border = Color.Transparent,
                    ),
                    primary,
                )
                val secondary = chatButtonColors(colors = colors, variant = ChatButtonVariant.Secondary, enabled = true)
                assertEquals(
                    ChatButtonColors(
                        container = colors.surface.secondary,
                        content = colors.text.primary,
                        border = colors.border.primary,
                    ),
                    secondary,
                )
                assertEquals(
                    secondary.copy(container = colors.surface.tertiary),
                    chatButtonColors(
                        colors = colors,
                        variant = ChatButtonVariant.Secondary,
                        enabled = true,
                        hovered = true,
                    ),
                )
                val outlined = chatButtonColors(colors = colors, variant = ChatButtonVariant.Outlined, enabled = true)
                assertEquals(
                    ChatButtonColors(
                        container = Color.Transparent,
                        content = colors.text.secondary,
                        border = colors.border.primary,
                    ),
                    outlined,
                )
                assertEquals(
                    outlined.copy(content = colors.text.primary),
                    chatButtonColors(
                        colors = colors,
                        variant = ChatButtonVariant.Outlined,
                        enabled = true,
                        pressed = true,
                    ),
                )
                val text = chatButtonColors(colors = colors, variant = ChatButtonVariant.Text, enabled = true)
                assertEquals(
                    ChatButtonColors(
                        container = Color.Transparent,
                        content = colors.text.secondary,
                        border = Color.Transparent,
                    ),
                    text,
                )
                assertEquals(
                    text.copy(container = colors.surface.tertiary),
                    chatButtonColors(colors = colors, variant = ChatButtonVariant.Text, enabled = true, pressed = true),
                )
                assertEquals(
                    ChatButtonColors(
                        container = Color.Transparent,
                        content = colors.status.danger,
                        border = colors.border.primary,
                    ),
                    chatButtonColors(
                        colors = colors,
                        variant = ChatButtonVariant.Danger,
                        enabled = true,
                        pressed = true,
                    ),
                )
            }
        }
    }

    @Test
    fun providerCoversEveryButtonStateInBothModes() {
        val buttons = ChatButtonPreviewParameterProvider().values.toList()
        assertEquals(60, buttons.size)
        for (variant in ChatButtonVariant.entries) {
            for (dark in listOf(false, true)) {
                assertEquals(
                    ChatButtonPreviewState.entries.toSet(),
                    buttons.filter { it.variant == variant && it.darkTheme == dark }.map { it.state }.toSet(),
                )
            }
        }
    }
}
