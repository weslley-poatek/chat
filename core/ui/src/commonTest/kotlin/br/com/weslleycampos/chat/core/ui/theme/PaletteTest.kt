package br.com.weslleycampos.chat.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PaletteTest {
    @Test
    fun everyPaletteSharesTheNeutralsAndChangesOnlyTheAccent() {
        assertEquals(
            listOf("SONNET", "OPUS", "GPT5", "O3", "GEMINI", "LLAMA", "DEEPSEEK", "MISTRAL"),
            ChatPalette.entries.map { it.name },
        )
        for (palette in ChatPalette.entries) {
            for (dark in listOf(false, true)) {
                val colors = palette.colors(dark)
                val base = if (dark) CoralDark else CoralLight
                assertEquals(dark, colors.isDark)
                assertEquals(base.copy(accent = colors.accent), colors)
                assertEquals(White, colors.surface.knob)
                assertEquals(Black.copy(alpha = 0.55f), colors.surface.scrim)
                assertEquals(if (dark) Slate16 else White, colors.text.onAccent)
                assertEquals(if (dark) GreenDark.accent.primary else GreenLight.accent.primary, colors.status.success)
                assertEquals(colors.text.primary.copy(alpha = 0.38f), colors.text.disabled)
                assertEquals(colors.border.primary.copy(alpha = 0.38f), colors.border.disabled)
                assertNotEquals(colors.surface.primary, colors.accent.primary)
            }
            assertEquals(palette.light.accent.glow, palette.dark.accent.glow)
        }
    }

    @Test
    fun modelsOfTheSameProviderHueShareTheirAccent() {
        assertEquals(ChatPalette.SONNET.dark, ChatPalette.OPUS.dark)
        assertEquals(ChatPalette.SONNET.light, ChatPalette.OPUS.light)
        assertEquals(ChatPalette.GPT5.dark, ChatPalette.O3.dark)
        assertEquals(ChatPalette.GPT5.light, ChatPalette.O3.light)
        val hues = listOf(
            ChatPalette.SONNET,
            ChatPalette.GPT5,
            ChatPalette.GEMINI,
            ChatPalette.LLAMA,
            ChatPalette.DEEPSEEK,
            ChatPalette.MISTRAL,
        )
        for (dark in listOf(false, true)) {
            assertEquals(hues.size, hues.map { it.colors(dark).accent.primary }.distinct().size)
        }
        assertEquals(ChatPalette.SONNET, ChatPalette.Default)
    }

    @Test
    fun gradientsFollowTheAccentAndSurfacesOfEveryPalette() {
        for (palette in ChatPalette.entries) {
            for (dark in listOf(false, true)) {
                val colors = palette.colors(dark)
                val gradients = palette.gradients(dark)
                assertEquals(
                    listOf(colors.accent.glow.copy(alpha = 0.12f), colors.accent.glow.copy(alpha = 0f)),
                    gradients.voiceGlow.colors,
                )
                assertEquals(
                    listOf(colors.accent.glow.copy(alpha = 0.22f), colors.accent.glow.copy(alpha = 0f)),
                    gradients.voiceGlowSpeaking.colors,
                )
                assertEquals(listOf(0f, 0.6f), gradients.voiceGlow.stops)
                assertEquals(listOf(colors.surface.secondary, colors.surface.tertiary), gradients.placeholder.colors)
            }
        }
    }

    @Test
    fun materialColorsAreDebugOnly() {
        assertEquals(Color.Magenta, debugColorScheme.primary)
        assertEquals(Color.Magenta, debugColorScheme.surface)
        assertEquals(Color.Magenta, debugColorScheme.onSurface)
    }
}
