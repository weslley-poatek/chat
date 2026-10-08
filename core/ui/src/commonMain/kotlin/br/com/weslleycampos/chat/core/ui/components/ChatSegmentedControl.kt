package br.com.weslleycampos.chat.core.ui.components

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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

private val SegmentHorizontalPadding = 9.dp

@Composable
fun ChatSegmentedControl(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = ChatTheme.shapes.compact

    Row(
        modifier = modifier
            .border(width = 1.dp, color = ChatTheme.colors.border.primary, shape = shape)
            .padding(ChatTheme.spacing.xxxSmall)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xxxSmall),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ChatSegment(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = ChatTheme.shapes.tag
    val (container, foreground) = chatSegmentColors(colors = ChatTheme.colors, selected = selected)
    val fadeModifier = if (enabled) Modifier else Modifier.alpha(DisabledAlpha)
    val focusModifier = if (focused && enabled) {
        Modifier.border(width = 2.dp, color = ChatTheme.colors.text.secondary, shape = shape).padding(2.dp)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(fadeModifier)
            .height(ChatTheme.sizes.medium)
            .then(focusModifier)
            .background(color = container, shape = shape)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = SegmentHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = ChatTheme.typography.labelSmall, color = foreground, maxLines = 1)
    }
}

internal fun chatSegmentColors(colors: ChatColors, selected: Boolean): Pair<Color, Color> = if (selected) {
    colors.text.primary to colors.surface.primary
} else {
    Color.Transparent to colors.text.secondary
}

enum class ChatSegmentPreviewState {
    Default,
    Hovered,
    Pressed,
    Focused,
    Disabled,
}

data class ChatSegmentPreviewCase(
    val selected: Boolean,
    val state: ChatSegmentPreviewState,
    val darkTheme: Boolean,
) {
    val name: String
        get() = "${if (selected) "Selected" else "Unselected"} · $state · ${if (darkTheme) "Dark" else "Light"}"
}

class ChatSegmentPreviewParameterProvider : PreviewParameterProvider<ChatSegmentPreviewCase> {
    override val values: Sequence<ChatSegmentPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for (selected in listOf(false, true)) {
                for (state in ChatSegmentPreviewState.entries) {
                    yield(ChatSegmentPreviewCase(selected = selected, state = state, darkTheme = dark))
                }
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@Preview(name = "Chat segmented controls", widthDp = 320)
@Composable
private fun ChatSegmentedControlPreview(
    @PreviewParameter(ChatSegmentPreviewParameterProvider::class) case: ChatSegmentPreviewCase,
) {
    val source = remember(case.state) { MutableInteractionSource() }
    LaunchedEffect(source) {
        withFrameNanos { }
        when (case.state) {
            ChatSegmentPreviewState.Hovered -> source.emit(HoverInteraction.Enter())
            ChatSegmentPreviewState.Pressed -> source.emit(PressInteraction.Press(Offset.Zero))
            ChatSegmentPreviewState.Focused -> source.emit(FocusInteraction.Focus())
            else -> Unit
        }
    }
    val target = 1
    val selected = if (case.selected) target else 0
    ChatTheme(darkTheme = case.darkTheme) {
        Surface(color = ChatTheme.colors.surface.primary, contentColor = ChatTheme.colors.text.primary) {
            Column(
                modifier = Modifier.padding(ChatTheme.spacing.xxLarge),
                verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
            ) {
                Text(text = case.name, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
                ChatSegmentedControl {
                    listOf("system", "dark", "light").forEachIndexed { index, label ->
                        ChatSegment(
                            selected = index == selected,
                            onClick = {},
                            label = label,
                            enabled = index != target || case.state != ChatSegmentPreviewState.Disabled,
                            interactionSource = if (index == target) source else null,
                        )
                    }
                }
            }
        }
    }
}
