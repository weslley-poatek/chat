package br.com.weslleycampos.chat.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.theme.ChatColors
import br.com.weslleycampos.chat.core.ui.theme.DisabledAlpha

enum class ChatButtonVariant {
    Primary,
    Secondary,
    Outlined,
    Text,
    Danger,
}

enum class ChatButtonSize {
    Large,
    Small,
}

@Composable
fun ChatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ChatButtonVariant = ChatButtonVariant.Primary,
    size: ChatButtonSize = ChatButtonSize.Large,
    enabled: Boolean = true,
    loading: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val interactive = enabled && !loading
    val large = size == ChatButtonSize.Large
    val shape = if (large) ChatTheme.shapes.control else ChatTheme.shapes.compact
    val buttonColors = chatButtonColors(
        colors = ChatTheme.colors,
        variant = variant,
        enabled = interactive,
        hovered = hovered,
        pressed = pressed,
    )
    val fadeModifier = if (!enabled && !loading) Modifier.alpha(DisabledAlpha) else Modifier
    val focusModifier = if (focused && interactive) {
        Modifier.border(width = 2.dp, color = ChatTheme.colors.text.secondary, shape = shape).padding(2.dp)
    } else {
        Modifier
    }
    val borderModifier = if (buttonColors.border != Color.Transparent) {
        Modifier.border(width = 1.dp, color = buttonColors.border, shape = shape)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .then(fadeModifier)
            .heightIn(min = if (large) ChatTheme.sizes.xxxLarge else ChatTheme.sizes.large)
            .then(focusModifier)
            .background(color = buttonColors.container, shape = shape)
            .then(borderModifier)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = interactive,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = if (large) ChatTheme.spacing.xLarge else ChatTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(
            space = ChatTheme.spacing.small,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides buttonColors.content) {
            ProvideTextStyle(chatButtonTextStyle(variant = variant, size = size)) {
                content()
                if (loading) {
                    ChatButtonSpinner(color = buttonColors.content)
                }
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun chatButtonTextStyle(variant: ChatButtonVariant, size: ChatButtonSize): TextStyle {
    val typography = ChatTheme.typography
    val primary = variant == ChatButtonVariant.Primary

    return when (size) {
        ChatButtonSize.Large -> if (primary) typography.labelLarge else typography.titleLarge
        ChatButtonSize.Small -> if (primary) {
            typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
        } else {
            typography.labelSmall
        }
    }
}

@Composable
private fun ChatButtonSpinner(color: Color, modifier: Modifier = Modifier) {
    val rotation by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 700, easing = LinearEasing)),
    )
    Canvas(modifier = modifier.size(ChatTheme.sizes.xxSmall)) {
        val stroke = 2.dp.toPx()
        rotate(degrees = rotation) {
            drawArc(
                color = color,
                startAngle = -45f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(x = stroke / 2, y = stroke / 2),
                size = Size(width = size.width - stroke, height = size.height - stroke),
                style = Stroke(width = stroke),
            )
        }
    }
}

internal data class ChatButtonColors(
    val container: Color,
    val content: Color,
    val border: Color,
)

internal fun chatButtonColors(
    colors: ChatColors,
    variant: ChatButtonVariant,
    enabled: Boolean,
    hovered: Boolean = false,
    pressed: Boolean = false,
): ChatButtonColors {
    val active = enabled && (hovered || pressed)
    return when (variant) {
        ChatButtonVariant.Primary -> ChatButtonColors(
            container = colors.text.primary,
            content = colors.surface.primary,
            border = Color.Transparent,
        )
        ChatButtonVariant.Secondary -> ChatButtonColors(
            container = if (active) colors.surface.tertiary else colors.surface.secondary,
            content = colors.text.primary,
            border = colors.border.primary,
        )
        ChatButtonVariant.Outlined -> ChatButtonColors(
            container = Color.Transparent,
            content = if (active) colors.text.primary else colors.text.secondary,
            border = colors.border.primary,
        )
        ChatButtonVariant.Text -> ChatButtonColors(
            container = if (active) colors.surface.tertiary else Color.Transparent,
            content = colors.text.secondary,
            border = Color.Transparent,
        )
        ChatButtonVariant.Danger -> ChatButtonColors(
            container = Color.Transparent,
            content = colors.status.danger,
            border = colors.border.primary,
        )
    }
}

enum class ChatButtonPreviewState {
    Default,
    Hovered,
    Pressed,
    Focused,
    Disabled,
    Loading,
}

data class ChatButtonPreviewCase(
    val variant: ChatButtonVariant,
    val state: ChatButtonPreviewState,
    val darkTheme: Boolean,
) {
    val name: String get() = "$variant · $state · ${if (darkTheme) "Dark" else "Light"}"
}

class ChatButtonPreviewParameterProvider : PreviewParameterProvider<ChatButtonPreviewCase> {
    override val values: Sequence<ChatButtonPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for (variant in ChatButtonVariant.entries) {
                for (state in ChatButtonPreviewState.entries) {
                    yield(ChatButtonPreviewCase(variant = variant, state = state, darkTheme = dark))
                }
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@Preview(name = "Chat buttons", widthDp = 360)
@Composable
private fun ChatButtonPreview(
    @PreviewParameter(ChatButtonPreviewParameterProvider::class) case: ChatButtonPreviewCase,
) {
    val labels = when (case.variant) {
        ChatButtonVariant.Primary -> "Sign in" to "save"
        ChatButtonVariant.Secondary -> "Continue with GitHub" to "⑂ fork here"
        ChatButtonVariant.Outlined -> "Continue with this →" to "cancel"
        ChatButtonVariant.Text -> "Forgot password?" to "copy"
        ChatButtonVariant.Danger -> "clear all memory" to "delete"
    }
    ChatTheme(darkTheme = case.darkTheme) {
        Surface(color = ChatTheme.colors.surface.primary, contentColor = ChatTheme.colors.text.primary) {
            Column(
                modifier = Modifier.padding(ChatTheme.spacing.xxLarge),
                verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
            ) {
                Text(text = case.name, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
                for (size in ChatButtonSize.entries) {
                    val source = remember(case.state) { MutableInteractionSource() }
                    LaunchedEffect(source) {
                        withFrameNanos { }
                        when (case.state) {
                            ChatButtonPreviewState.Hovered -> source.emit(HoverInteraction.Enter())
                            ChatButtonPreviewState.Pressed -> source.emit(PressInteraction.Press(Offset.Zero))
                            ChatButtonPreviewState.Focused -> source.emit(FocusInteraction.Focus())
                            else -> Unit
                        }
                    }
                    ChatButton(
                        onClick = {},
                        modifier = if (size == ChatButtonSize.Large) Modifier.fillMaxWidth() else Modifier,
                        variant = case.variant,
                        size = size,
                        enabled = case.state != ChatButtonPreviewState.Disabled,
                        loading = case.state == ChatButtonPreviewState.Loading,
                        interactionSource = source,
                    ) {
                        Text(if (size == ChatButtonSize.Large) labels.first else labels.second)
                    }
                }
            }
        }
    }
}
