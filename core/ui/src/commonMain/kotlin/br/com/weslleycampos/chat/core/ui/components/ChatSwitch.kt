package br.com.weslleycampos.chat.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.theme.ChatColors
import br.com.weslleycampos.chat.core.ui.theme.DisabledAlpha

private const val TrackAnimationMillis = 150

@Composable
fun ChatSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = ChatTheme.shapes.pill
    val (trackTarget, knob) = chatSwitchColors(colors = ChatTheme.colors, checked = checked)
    val track by animateColorAsState(
        targetValue = trackTarget,
        animationSpec = tween(durationMillis = TrackAnimationMillis),
    )
    val fadeModifier = if (enabled) Modifier else Modifier.alpha(DisabledAlpha)
    val focusModifier = if (focused && enabled) {
        Modifier.border(width = 2.dp, color = ChatTheme.colors.text.secondary, shape = shape).padding(2.dp)
    } else {
        Modifier
    }
    val toggleModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(fadeModifier)
            .then(focusModifier)
            .then(toggleModifier)
            .size(width = ChatTheme.sizes.xxLarge, height = ChatTheme.sizes.medium)
            .background(color = track, shape = shape)
            .padding(ChatTheme.spacing.xxxSmall),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(ChatTheme.sizes.small)
                .dropShadow(shape = shape, shadow = ChatTheme.elevation.knob)
                .background(color = knob, shape = shape),
        )
    }
}

internal fun chatSwitchColors(colors: ChatColors, checked: Boolean): Pair<Color, Color> {
    val track = if (checked) colors.accent.primary else colors.surface.tertiary
    return track to colors.surface.knob
}

enum class ChatSwitchPreviewState {
    Default,
    Hovered,
    Pressed,
    Focused,
    Disabled,
}

data class ChatSwitchPreviewCase(
    val checked: Boolean,
    val state: ChatSwitchPreviewState,
    val darkTheme: Boolean,
) {
    val name: String get() = "${if (checked) "On" else "Off"} · $state · ${if (darkTheme) "Dark" else "Light"}"
}

class ChatSwitchPreviewParameterProvider : PreviewParameterProvider<ChatSwitchPreviewCase> {
    override val values: Sequence<ChatSwitchPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for (checked in listOf(false, true)) {
                for (state in ChatSwitchPreviewState.entries) {
                    yield(ChatSwitchPreviewCase(checked = checked, state = state, darkTheme = dark))
                }
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@Preview(name = "Chat switches", widthDp = 320)
@Composable
private fun ChatSwitchPreview(
    @PreviewParameter(ChatSwitchPreviewParameterProvider::class) case: ChatSwitchPreviewCase,
) {
    val source = remember(case.state) { MutableInteractionSource() }
    LaunchedEffect(source) {
        withFrameNanos { }
        when (case.state) {
            ChatSwitchPreviewState.Hovered -> source.emit(HoverInteraction.Enter())
            ChatSwitchPreviewState.Pressed -> source.emit(PressInteraction.Press(Offset.Zero))
            ChatSwitchPreviewState.Focused -> source.emit(FocusInteraction.Focus())
            else -> Unit
        }
    }
    ChatTheme(darkTheme = case.darkTheme) {
        Surface(color = ChatTheme.colors.surface.primary, contentColor = ChatTheme.colors.text.primary) {
            Column(
                modifier = Modifier.padding(ChatTheme.spacing.xxLarge),
                verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
            ) {
                Text(text = case.name, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
                ChatSwitch(
                    checked = case.checked,
                    onCheckedChange = {},
                    enabled = case.state != ChatSwitchPreviewState.Disabled,
                    interactionSource = source,
                )
            }
        }
    }
}
