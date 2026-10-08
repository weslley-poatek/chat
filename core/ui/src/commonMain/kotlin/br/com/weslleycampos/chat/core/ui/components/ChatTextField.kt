package br.com.weslleycampos.chat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.theme.ChatColors
import br.com.weslleycampos.chat.core.ui.theme.DisabledAlpha

private const val SelectionAlpha = 0.4f

@Composable
fun ChatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val fieldColors = chatTextFieldColors(colors = ChatTheme.colors, enabled = enabled, focused = focused)
    val shape = ChatTheme.shapes.control
    val textStyle = ChatTheme.typography.input
    val fadeModifier = if (enabled) Modifier else Modifier.alpha(DisabledAlpha)
    val selectionColors = TextSelectionColors(
        handleColor = fieldColors.selection,
        backgroundColor = fieldColors.selection.copy(alpha = SelectionAlpha),
    )

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.then(fadeModifier).height(ChatTheme.sizes.xxxLarge),
            enabled = enabled,
            textStyle = textStyle.copy(color = fieldColors.text),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            visualTransformation = visualTransformation,
            interactionSource = source,
            cursorBrush = SolidColor(fieldColors.text),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .background(color = fieldColors.container, shape = shape)
                        .border(width = 1.dp, color = fieldColors.border, shape = shape)
                        .padding(
                            start = ChatTheme.spacing.xLarge,
                            end = if (trailingIcon == null) ChatTheme.spacing.xLarge else ChatTheme.spacing.xSmall,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(text = placeholder, style = textStyle, color = fieldColors.placeholder, maxLines = 1)
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) {
                        CompositionLocalProvider(LocalContentColor provides fieldColors.placeholder) {
                            ProvideTextStyle(value = ChatTheme.typography.labelSmall, content = trailingIcon)
                        }
                    }
                }
            },
        )
    }
}

internal data class ChatTextFieldColors(
    val container: Color,
    val border: Color,
    val text: Color,
    val placeholder: Color,
    val selection: Color,
)

internal fun chatTextFieldColors(
    colors: ChatColors,
    enabled: Boolean,
    focused: Boolean = false,
) = ChatTextFieldColors(
    container = colors.surface.primary,
    border = if (enabled && focused) colors.text.secondary else colors.border.primary,
    text = colors.text.primary,
    placeholder = colors.text.tertiary,
    selection = colors.accent.primary,
)

enum class ChatTextFieldPreviewState(
    val displayName: String,
    val enabled: Boolean = true,
    val focused: Boolean = false,
) {
    Empty("Empty"),
    Filled("Filled"),
    FocusedEmpty("Focused empty", focused = true),
    Focused("Focused", focused = true),
    DisabledEmpty("Disabled empty", enabled = false),
    Disabled("Disabled", enabled = false),
    Password("Password"),
}

data class ChatTextFieldPreviewCase(val state: ChatTextFieldPreviewState, val darkTheme: Boolean) {
    val name: String get() = "${state.displayName} · ${if (darkTheme) "Dark" else "Light"}"
}

class ChatTextFieldPreviewParameterProvider : PreviewParameterProvider<ChatTextFieldPreviewCase> {
    override val values: Sequence<ChatTextFieldPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for (state in ChatTextFieldPreviewState.entries) {
                yield(ChatTextFieldPreviewCase(state = state, darkTheme = dark))
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).name
}

@Preview(name = "Chat text fields", widthDp = 360)
@Composable
private fun ChatTextFieldPreview(
    @PreviewParameter(ChatTextFieldPreviewParameterProvider::class) case: ChatTextFieldPreviewCase,
) {
    val password = case.state == ChatTextFieldPreviewState.Password
    val value = when (case.state) {
        ChatTextFieldPreviewState.Empty,
        ChatTextFieldPreviewState.FocusedEmpty,
        ChatTextFieldPreviewState.DisabledEmpty -> ""
        ChatTextFieldPreviewState.Password -> "correct horse"
        ChatTextFieldPreviewState.Filled,
        ChatTextFieldPreviewState.Focused,
        ChatTextFieldPreviewState.Disabled -> "you@example.com"
    }
    val source = remember(case.state) { MutableInteractionSource() }
    LaunchedEffect(source) {
        if (case.state.focused) {
            withFrameNanos { }
            source.emit(FocusInteraction.Focus())
        }
    }
    ChatTheme(darkTheme = case.darkTheme) {
        Surface(color = ChatTheme.colors.surface.primary, contentColor = ChatTheme.colors.text.primary) {
            Column(
                modifier = Modifier.padding(ChatTheme.spacing.xxLarge),
                verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
            ) {
                Text(text = case.name, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
                ChatTextField(
                    value = value,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = if (password) "password" else "email",
                    enabled = case.state.enabled,
                    trailingIcon = if (password) {
                        { Text(text = "show", modifier = Modifier.padding(horizontal = ChatTheme.spacing.medium)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (password) KeyboardType.Password else KeyboardType.Email,
                    ),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    interactionSource = source,
                )
            }
        }
    }
}
