package br.com.weslleycampos.chat.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val VoiceGlowAlpha = 0.12f
private const val VoiceGlowSpeakingAlpha = 0.22f
private const val VoiceGlowExtent = 0.6f

@Immutable
data class ChatRadialGradient(
    val colors: List<Color>,
    val stops: List<Float>,
)

@Immutable
data class ChatStripes(
    val colors: List<Color>,
    val stripeWidth: Dp,
    val cssAngleDegrees: Float,
)

@Immutable
data class ChatGradients(
    val voiceGlow: ChatRadialGradient,
    val voiceGlowSpeaking: ChatRadialGradient,
    val placeholder: ChatStripes,
)

fun ChatRadialGradient.toBrush(): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader = RadialGradientShader(
        center = size.center,
        radius = size.center.getDistance(),
        colors = colors,
        colorStops = stops,
    )
}

fun ChatStripes.toBrush(density: Density): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = cssAngleDegrees.toDouble() * PI / 180.0
        val period = with(density) { stripeWidth.toPx() } * colors.size
        val stops = colors.indices.flatMap { index ->
            listOf(index.toFloat() / colors.size, (index + 1).toFloat() / colors.size)
        }

        return LinearGradientShader(
            from = Offset.Zero,
            to = Offset(x = sin(radians).toFloat() * period, y = -cos(radians).toFloat() * period),
            colors = colors.flatMap { listOf(it, it) },
            colorStops = stops,
            tileMode = TileMode.Repeated,
        )
    }
}

private fun voiceGlow(color: Color, alpha: Float) = ChatRadialGradient(
    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
    stops = listOf(0f, VoiceGlowExtent),
)

fun ChatPalette.gradients(dark: Boolean): ChatGradients {
    val colors = colors(dark)

    return ChatGradients(
        voiceGlow = voiceGlow(color = colors.accent.glow, alpha = VoiceGlowAlpha),
        voiceGlowSpeaking = voiceGlow(color = colors.accent.glow, alpha = VoiceGlowSpeakingAlpha),
        placeholder = ChatStripes(
            colors = listOf(colors.surface.secondary, colors.surface.tertiary),
            stripeWidth = 6.dp,
            cssAngleDegrees = 135f,
        ),
    )
}

val LocalChatGradients = staticCompositionLocalOf<ChatGradients> {
    error("No ChatGradients provided. Wrap your content in ChatTheme { ... }")
}
