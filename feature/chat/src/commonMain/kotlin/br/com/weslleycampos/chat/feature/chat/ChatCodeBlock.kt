package br.com.weslleycampos.chat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.theme.ChatSyntaxColors
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_code_copied
import br.com.weslleycampos.chat.feature.chat.resources.chat_code_copy
import org.jetbrains.compose.resources.stringResource

/**
 * A `code` block (frame lines 167–172): the language and "copy" / "copied" over a hairline, then
 * the code, syntax coloured, unwrapped and scrolling sideways, with the caret while it streams.
 */
@Composable
internal fun CodeBlock(
    block: BlockUi.Code,
    copied: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ChatTheme.shapes.control
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color = ChatTheme.colors.surface.secondary)
            .border(width = HairlineWidth, color = ChatTheme.colors.border.primary, shape = shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChatTheme.sizes.large)
                .bottomHairline(ChatTheme.colors.border.primary)
                .padding(start = ChatTheme.spacing.large, end = ChatTheme.spacing.xxSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = block.language, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
            CopyButton(copied = copied, onClick = onCopy)
        }
        CodeText(block = block)
    }
}

@Composable
private fun CodeText(block: BlockUi.Code) {
    val syntax = ChatTheme.colors.syntax
    val code = remember(block.tokens, syntax) { highlighted(block.tokens, syntax) }
    val text = if (block.caret) {
        val alpha by caretAlpha(StreamingCaretBlinkMillis)
        val caret = caretStyle(alpha)
        buildAnnotatedString {
            append(code)
            withStyle(caret) { append(Caret) }
        }
    } else {
        code
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ChatTheme.spacing.large, vertical = ChatTheme.spacing.medium),
        style = ChatTheme.typography.code,
        color = ChatTheme.colors.text.primary,
        softWrap = false,
    )
}

/** The block's text with each token in its syntax colour. */
internal fun highlighted(tokens: List<CodeToken>, syntax: ChatSyntaxColors): AnnotatedString = buildAnnotatedString {
    for (token in tokens) {
        val color = token.kind.color(syntax)
        if (color == Color.Unspecified) {
            append(token.text)
        } else {
            withStyle(SpanStyle(color = color)) { append(token.text) }
        }
    }
}

/** "copy" / "copied": a 26 tall mono button that takes `--bg3` while pressed. */
@Composable
private fun CopyButton(copied: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = ChatTheme.shapes.compact
    Box(
        modifier = Modifier
            .height(ChatTheme.sizes.medium)
            .background(color = if (pressed) ChatTheme.colors.surface.tertiary else Color.Transparent, shape = shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = ChatTheme.spacing.small)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (copied) FeatureChatRes.string.chat_code_copied else FeatureChatRes.string.chat_code_copy,
            ),
            style = ChatTheme.typography.labelSmall,
            color = ChatTheme.colors.text.secondary,
        )
    }
}
