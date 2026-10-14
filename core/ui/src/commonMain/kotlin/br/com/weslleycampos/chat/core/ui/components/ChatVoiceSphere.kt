package br.com.weslleycampos.chat.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val ParticleCount = 1000
private const val ParticleSeed = 7

// Positions are packed as x, y, z per particle.
private const val Axes = 3
private const val MaxFrameSeconds = 0.05f
private const val NanosPerSecond = 1_000_000_000f

// The sphere's radius as a fraction of the shorter side, and the glow's radius in sphere radii.
private const val SphereRadiusFraction = 0.27f
private const val GlowRadius = 1.94f
private const val GlowFadeStop = 0.6f
private const val GlowAlpha = 0.12f
private const val SpeakingGlowAlpha = 0.22f
private const val GlowAnimationMillis = 600

enum class ChatVoicePhase {
    Listening,
    Thinking,
    Speaking,
}

/**
 * The voice mode particle field: 1,000 points on a sphere, tinted with the palette's accent. They swell with
 * the voice while [ChatVoicePhase.Listening], orbit as a tilted ring while [ChatVoicePhase.Thinking], and pulse
 * outwards while [ChatVoicePhase.Speaking]. The amplitude is simulated, as in the prototype.
 */
@Composable
fun ChatVoiceSphere(
    modifier: Modifier = Modifier,
    phase: ChatVoicePhase = ChatVoicePhase.Listening,
) {
    val colors = ChatTheme.colors
    val particles = remember { ChatVoiceParticles() }
    val currentPhase by rememberUpdatedState(phase)
    val glowAlpha by animateFloatAsState(
        targetValue = if (phase == ChatVoicePhase.Speaking) SpeakingGlowAlpha else GlowAlpha,
        animationSpec = tween(durationMillis = GlowAnimationMillis),
    )

    LaunchedEffect(particles) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / NanosPerSecond).coerceAtMost(MaxFrameSeconds)
                particles.step(dt = dt, phase = currentPhase)
                last = now
            }
        }
    }

    Spacer(
        modifier = modifier
            .drawBehind { drawVoiceGlow(color = colors.accent.glow.copy(alpha = glowAlpha)) }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawVoiceParticles(
                    particles = particles,
                    color = colors.accent.particle,
                    dark = colors.isDark,
                )
            },
    )
}

private fun DrawScope.drawVoiceGlow(color: Color) {
    val radius = size.minDimension * SphereRadiusFraction * GlowRadius
    drawCircle(
        brush = Brush.radialGradient(
            0f to color,
            GlowFadeStop to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
    )
}

private fun DrawScope.drawVoiceParticles(particles: ChatVoiceParticles, color: Color, dark: Boolean) {
    val t = particles.time
    val radius = size.minDimension * SphereRadiusFraction
    val listening = particles.mix(ChatVoicePhase.Listening)
    val thinking = particles.mix(ChatVoicePhase.Thinking)
    val speaking = particles.mix(ChatVoicePhase.Speaking)
    val amplitude = particles.amplitude
    val rotationY = t * 0.25f + thinking * t * 0.9f
    val rotationX = 0.35f + 0.25f * sin(t * 0.3f)
    val cosY = cos(rotationY)
    val sinY = sin(rotationY)
    val cosX = cos(rotationX)
    val sinX = sin(rotationX)
    val themeAlpha = if (dark) 1f else 0.85f
    val pointSize = 1.dp.toPx() * (1 + amplitude * 0.5f * listening)
    // Additive blending makes overlapping points glow on dark surfaces, as the prototype's "lighter" canvas does.
    val blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver
    val positions = particles.positions

    for (i in 0 until particles.count) {
        val j = i * Axes
        val x = positions[j]
        val y = positions[j + 1]
        val z = positions[j + 2]
        val x1 = x * cosY + z * sinY
        val z0 = -x * sinY + z * cosY
        val y1 = y * cosX - z0 * sinX
        val z1 = y * sinX + z0 * cosX
        val perspective = 1 / (1 + z1 / 4.5f)
        val nearness = 1 - (z1 + 1) / 2
        val alpha = (0.25f + 0.75f * nearness) * (0.55f + 0.45f * speaking + 0.15f * amplitude) * themeAlpha
        val side = (0.9f + 1.6f * nearness) * pointSize
        drawRect(
            color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
            topLeft = Offset(
                x = center.x + x1 * radius * perspective - side / 2,
                y = center.y + y1 * radius * perspective - side / 2,
            ),
            size = Size(width = side, height = side),
            blendMode = blendMode,
        )
    }
}

/**
 * The particles' state, in sphere radii. [step] eases each point towards the shape of the current phase; the
 * rotation and perspective are applied when drawing. [time] is snapshot state, so every step redraws the field.
 */
internal class ChatVoiceParticles(
    val count: Int = ParticleCount,
    random: Random = Random(ParticleSeed),
) {
    private val base = FloatArray(count * Axes)
    private val offsets = FloatArray(count)
    private val speeds = FloatArray(count)
    private val mix = FloatArray(ChatVoicePhase.entries.size)

    val positions = FloatArray(count * Axes)

    var time by mutableFloatStateOf(0f)
        private set

    var amplitude = 0.2f
        private set

    init {
        val goldenAngle = (PI * (3 - sqrt(5.0))).toFloat()
        for (i in 0 until count) {
            val y = 1 - 2 * (i + 0.5f) / count
            val ring = sqrt(1 - y * y)
            val theta = i * goldenAngle
            val speed = 0.6f + random.nextFloat() * 0.8f
            val j = i * Axes
            base[j] = ring * cos(theta)
            base[j + 1] = y
            base[j + 2] = ring * sin(theta)
            offsets[i] = random.nextFloat() * 2 * PI.toFloat()
            speeds[i] = speed
        }
        base.copyInto(positions)
        mix[ChatVoicePhase.Listening.ordinal] = 1f
    }

    fun mix(phase: ChatVoicePhase): Float = mix[phase.ordinal]

    fun step(dt: Float, phase: ChatVoicePhase) {
        val mixRate = min(1f, dt * 4)
        for (entry in ChatVoicePhase.entries) {
            val target = if (entry == phase) 1f else 0f
            mix[entry.ordinal] += (target - mix[entry.ordinal]) * mixRate
        }
        val t = time + dt
        val talk = if (phase == ChatVoicePhase.Thinking) {
            0.05f
        } else {
            max(0f, 0.35f + 0.4f * sin(t * 3.1f) + 0.25f * sin(t * 7.3f + 1) + 0.2f * sin(t * 13.7f))
        }
        val amplitudeRate = min(1f, dt * 10)
        amplitude += (talk - amplitude) * amplitudeRate

        val listening = mix(ChatVoicePhase.Listening)
        val thinking = mix(ChatVoicePhase.Thinking)
        val speaking = mix(ChatVoicePhase.Speaking)
        val follow = min(1f, dt * 6)
        for (i in 0 until count) {
            val j = i * Axes
            val bx = base[j]
            val by = base[j + 1]
            val bz = base[j + 2]
            val offset = offsets[i]
            // Listening: the sphere, deformed by the amplitude.
            val listenRadius = 0.92f + amplitude * 0.42f * (0.55f + 0.45f * sin(offset * 2 + t * 2.4f + by * 4))
            // Thinking: a tilted ring the points orbit along.
            val angle = offset + t * speeds[i] * 1.4f
            val ringRadius = 0.78f + 0.08f * sin(offset * 3 + t * 2)
            // Speaking: the sphere with radial pulses.
            val speakRadius = 1 + 0.13f * sin(t * 6.5f - by * 5) + amplitude * 0.18f
            val gx = bx * (listenRadius * listening + speakRadius * speaking) + cos(angle) * ringRadius * thinking
            val gy = by * (listenRadius * listening + speakRadius * speaking) +
                (sin(angle) * ringRadius * 0.28f + by * 0.12f) * thinking
            val gz = bz * (listenRadius * listening + speakRadius * speaking) + sin(angle) * ringRadius * thinking
            positions[j] += (gx - positions[j]) * follow
            positions[j + 1] += (gy - positions[j + 1]) * follow
            positions[j + 2] += (gz - positions[j + 2]) * follow
        }
        time = t
    }
}

data class ChatVoiceSpherePreviewCase(
    val phase: ChatVoicePhase,
    val darkTheme: Boolean,
) {
    val name: String get() = "$phase · ${if (darkTheme) "Dark" else "Light"}"
}

class ChatVoiceSpherePreviewParameterProvider : PreviewParameterProvider<ChatVoiceSpherePreviewCase> {
    override val values: Sequence<ChatVoiceSpherePreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for (phase in ChatVoicePhase.entries) {
                yield(ChatVoiceSpherePreviewCase(phase = phase, darkTheme = dark))
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@Preview(name = "Chat voice sphere", widthDp = 320)
@Composable
private fun ChatVoiceSpherePreview(
    @PreviewParameter(ChatVoiceSpherePreviewParameterProvider::class) case: ChatVoiceSpherePreviewCase,
) {
    ChatTheme(darkTheme = case.darkTheme) {
        Surface(color = ChatTheme.colors.surface.primary, contentColor = ChatTheme.colors.text.primary) {
            Column(
                modifier = Modifier.padding(ChatTheme.spacing.xxLarge),
                verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
            ) {
                Text(text = case.name, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
                ChatVoiceSphere(modifier = Modifier.size(288.dp), phase = case.phase)
            }
        }
    }
}
