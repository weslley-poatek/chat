package br.com.weslleycampos.chat.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.icons.ChatIcons
import br.com.weslleycampos.chat.core.ui.theme.ChatColors
import br.com.weslleycampos.chat.core.ui.theme.DisabledAlpha
import org.jetbrains.compose.resources.painterResource

private const val AccentTransitionMillis = 150

enum class ChatIconButtonVariant {
    Standard,
    Accent,
}

@Composable
fun ChatIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: ChatIconButtonVariant = ChatIconButtonVariant.Standard,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val shape = ChatTheme.shapes.iconButton
    val (containerTarget, foregroundTarget) = chatIconButtonColors(
        colors = ChatTheme.colors,
        variant = variant,
        enabled = enabled,
        hovered = hovered,
        pressed = pressed,
    )
    // The prototype's send button eases `background .15s, color .15s` between enabled and disabled;
    // the plain icon buttons have no transition.
    val colorSpec: AnimationSpec<Color> = if (variant == ChatIconButtonVariant.Accent) {
        tween(durationMillis = AccentTransitionMillis)
    } else {
        snap()
    }
    val container by animateColorAsState(targetValue = containerTarget, animationSpec = colorSpec)
    val foreground by animateColorAsState(targetValue = foregroundTarget, animationSpec = colorSpec)
    // A disabled Accent button has colours of its own instead of fading.
    val fadeModifier = if (enabled || variant == ChatIconButtonVariant.Accent) {
        Modifier
    } else {
        Modifier.alpha(DisabledAlpha)
    }
    val focusModifier = if (focused && enabled) {
        Modifier.border(width = 2.dp, color = ChatTheme.colors.text.secondary, shape = shape).padding(2.dp)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(fadeModifier)
            .size(ChatTheme.sizes.xxLarge)
            .then(focusModifier)
            .background(color = container, shape = shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides foreground, content = content)
    }
}

internal fun chatIconButtonColors(
    colors: ChatColors,
    variant: ChatIconButtonVariant,
    enabled: Boolean,
    hovered: Boolean = false,
    pressed: Boolean = false,
): Pair<Color, Color> = when (variant) {
    ChatIconButtonVariant.Standard -> {
        val background = if (enabled && (hovered || pressed)) colors.surface.secondary else Color.Transparent
        background to colors.text.secondary
    }
    // The prototype's `canSend` styles: the accent fill, or `--bg2` with `--fg3` while disabled.
    ChatIconButtonVariant.Accent -> if (enabled) {
        colors.accent.primary to colors.text.onAccent
    } else {
        colors.surface.secondary to colors.text.tertiary
    }
}

enum class ChatIconButtonPreviewState {
    Default,
    Hovered,
    Pressed,
    Focused,
    Disabled,
}

data class ChatIconButtonPreviewCase(
    val variant: ChatIconButtonVariant,
    val state: ChatIconButtonPreviewState,
    val darkTheme: Boolean,
) {
    val name: String get() = "$variant · $state · ${if (darkTheme) "Dark" else "Light"}"
}

class ChatIconButtonPreviewParameterProvider : PreviewParameterProvider<ChatIconButtonPreviewCase> {
    override val values: Sequence<ChatIconButtonPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for (variant in ChatIconButtonVariant.entries) {
                for (state in ChatIconButtonPreviewState.entries) {
                    yield(ChatIconButtonPreviewCase(variant = variant, state = state, darkTheme = dark))
                }
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@Preview(name = "Chat icon buttons", widthDp = 320)
@Composable
private fun ChatIconButtonPreview(
    @PreviewParameter(ChatIconButtonPreviewParameterProvider::class) case: ChatIconButtonPreviewCase,
) {
    val source = remember(case.state) { MutableInteractionSource() }
    LaunchedEffect(source) {
        withFrameNanos { }
        when (case.state) {
            ChatIconButtonPreviewState.Hovered -> source.emit(HoverInteraction.Enter())
            ChatIconButtonPreviewState.Pressed -> source.emit(PressInteraction.Press(Offset.Zero))
            ChatIconButtonPreviewState.Focused -> source.emit(FocusInteraction.Focus())
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
                ChatIconButton(
                    onClick = {},
                    contentDescription = if (case.variant == ChatIconButtonVariant.Accent) "Send" else "Menu",
                    variant = case.variant,
                    enabled = case.state != ChatIconButtonPreviewState.Disabled,
                    interactionSource = source,
                ) {
                    val icon = if (case.variant == ChatIconButtonVariant.Accent) ChatIcons.Send else ChatIcons.Menu
                    Icon(painter = painterResource(icon), contentDescription = null)
                }
            }
        }
    }
}
