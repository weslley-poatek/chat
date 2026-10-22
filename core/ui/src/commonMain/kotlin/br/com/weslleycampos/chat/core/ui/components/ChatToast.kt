package br.com.weslleycampos.chat.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.theme.ChatColors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val FadeInMillis = 150

object ChatToastDefaults {
    /** The prototype's `toastMsg`: a toast hides 1400 ms after it shows. */
    val Duration: Duration = 1400.milliseconds
}

/**
 * The prototype's toast: one line of mono 12 on an inverted pill that fades in over 150 ms. It
 * shows while [message] is not `null` and hides at once when it is; a new message replaces the
 * shown one in place. It takes no touches, and screen readers announce it.
 *
 * Timing is the caller's: clear [message] after [ChatToastDefaults.Duration], restarting on each
 * new one. Place it horizontally centred above the screen's bottom controls.
 */
@Composable
fun ChatToast(
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    // Remembered while a message shows, so a replacement keeps the opacity and only a toast that
    // appears after none fades in, as the prototype's element does. A static preview draws it whole.
    val inspecting = LocalInspectionMode.current
    val alpha = remember { Animatable(if (inspecting) 1f else 0f) }
    LaunchedEffect(alpha) {
        alpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = FadeInMillis, easing = EaseOut))
    }
    val (container, content) = chatToastColors(ChatTheme.colors)
    Text(
        text = message,
        modifier = modifier
            .graphicsLayer { this.alpha = alpha.value }
            .background(color = container, shape = ChatTheme.shapes.control)
            .padding(horizontal = ChatTheme.spacing.large, vertical = ChatTheme.spacing.small)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = ChatTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The prototype's `background:var(--fg); color:var(--bg)`: the screen's colours, inverted. */
internal fun chatToastColors(colors: ChatColors): Pair<Color, Color> = colors.text.primary to colors.surface.primary

enum class ChatToastPreviewState(val message: String?) {
    Hidden(message = null),
    Short(message = "archived"),
    Long(message = "forked · new conversation"),
}

data class ChatToastPreviewCase(val state: ChatToastPreviewState, val darkTheme: Boolean) {
    val name: String get() = "${state.name} · ${if (darkTheme) "Dark" else "Light"}"
}

class ChatToastPreviewParameterProvider : PreviewParameterProvider<ChatToastPreviewCase> {
    override val values: Sequence<ChatToastPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for (state in ChatToastPreviewState.entries) {
                yield(ChatToastPreviewCase(state = state, darkTheme = dark))
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@Preview(name = "Chat toasts", widthDp = 360)
@Composable
private fun ChatToastPreview(
    @PreviewParameter(ChatToastPreviewParameterProvider::class) case: ChatToastPreviewCase,
) {
    ChatTheme(darkTheme = case.darkTheme) {
        Surface(color = ChatTheme.colors.surface.primary, contentColor = ChatTheme.colors.text.primary) {
            Column(
                modifier = Modifier.padding(ChatTheme.spacing.xxLarge),
                verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
            ) {
                Text(text = case.name, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ChatToast(message = case.state.message)
                }
            }
        }
    }
}
