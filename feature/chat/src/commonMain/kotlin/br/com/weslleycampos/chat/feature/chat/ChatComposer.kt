package br.com.weslleycampos.chat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatIconButton
import br.com.weslleycampos.chat.core.ui.components.ChatIconButtonVariant
import br.com.weslleycampos.chat.core.ui.icons.ChatIcons
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_attach
import br.com.weslleycampos.chat.feature.chat.resources.chat_send
import br.com.weslleycampos.chat.feature.chat.resources.chat_voice
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The draft's `line-height:1.4`. */
private val DraftLineHeight = 1.4.em

/** The draft's `max-height:160px`. */
private val DraftMaxHeight = 160.dp

/** The draft's `padding:11px 12px`. */
private val DraftVerticalPadding = 11.dp

private const val SelectionAlpha = 0.4f

/**
 * The composer (frame lines 220–249): "+", the draft, the mic and Send, over a hairline. The draft
 * grows a line per line of text up to six, then scrolls; ⌘ / Ctrl + Enter sends from a hardware
 * keyboard. Send takes the accent only while [canSend]. [placeholder] shows while the draft is
 * empty: "message sonnet-4.5…", or "ask sonnet-4.5 and gpt-5…" while comparing.
 */
@Composable
internal fun ChatComposer(
    draft: String,
    draftRows: Int,
    placeholder: String,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .topHairline(ChatTheme.colors.border.primary)
            .background(ChatTheme.colors.surface.primary)
            .padding(
                start = ChatTheme.spacing.small,
                end = ChatTheme.spacing.small,
                top = ChatTheme.spacing.small,
                bottom = ChatTheme.spacing.xSmall,
            ),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xxSmall),
        verticalAlignment = Alignment.Bottom,
    ) {
        ChatIconButton(
            onClick = onAttachClick,
            contentDescription = stringResource(FeatureChatRes.string.chat_attach),
        ) {
            Icon(painter = painterResource(ChatIcons.Add), contentDescription = null)
        }
        DraftField(
            draft = draft,
            draftRows = draftRows,
            placeholder = placeholder,
            onDraftChange = onDraftChange,
            onSend = { if (canSend) onSend() },
            modifier = Modifier.weight(1f),
        )
        ChatIconButton(onClick = onVoiceClick, contentDescription = stringResource(FeatureChatRes.string.chat_voice)) {
            Icon(painter = painterResource(ChatIcons.Mic), contentDescription = null)
        }
        ChatIconButton(
            onClick = onSend,
            contentDescription = stringResource(FeatureChatRes.string.chat_send),
            variant = ChatIconButtonVariant.Accent,
            enabled = canSend,
        ) {
            Icon(painter = painterResource(ChatIcons.Send), contentDescription = null)
        }
    }
}

@Composable
private fun DraftField(
    draft: String,
    draftRows: Int,
    placeholder: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = ChatTheme.shapes.iconButton
    val textStyle = ChatTheme.typography.bodyLarge.copy(lineHeight = DraftLineHeight)
    val accent = ChatTheme.colors.accent.primary
    val selectionColors = remember(accent) {
        TextSelectionColors(handleColor = accent, backgroundColor = accent.copy(alpha = SelectionAlpha))
    }
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = modifier
                .heightIn(min = ChatTheme.sizes.xxLarge, max = DraftMaxHeight)
                .onPreviewKeyEvent { event ->
                    val send = event.type == KeyEventType.KeyDown &&
                        event.key == Key.Enter &&
                        (event.isMetaPressed || event.isCtrlPressed)
                    if (send) onSend()
                    send
                },
            textStyle = textStyle.copy(color = ChatTheme.colors.text.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = draftRows,
            maxLines = MaxDraftRows,
            interactionSource = source,
            cursorBrush = SolidColor(ChatTheme.colors.text.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .background(color = ChatTheme.colors.surface.secondary, shape = shape)
                        .border(
                            width = HairlineWidth,
                            color = if (focused) ChatTheme.colors.text.tertiary else ChatTheme.colors.border.primary,
                            shape = shape,
                        )
                        .padding(horizontal = ChatTheme.spacing.large, vertical = DraftVerticalPadding),
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = ChatTheme.colors.text.tertiary,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
