package br.com.weslleycampos.chat.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import br.com.weslleycampos.chat.core.ui.ChatTheme

// A reply's blocks and carets, drawn alike in the thread and in a compare card.

/**
 * A reply's blocks, one after another in the column that holds them: paragraphs and lists in
 * [textStyle], 15 px in the thread and 14 in a compare card, and code [compact] in a card.
 */
@Composable
internal fun ReplyBlocks(
    blocks: List<BlockUi>,
    copiedBlock: BlockKey?,
    onCopyCode: (BlockKey) -> Unit,
    textStyle: TextStyle,
    compact: Boolean = false,
) {
    for (block in blocks) {
        when (block) {
            is BlockUi.Paragraph -> Paragraph(block = block, textStyle = textStyle)
            is BlockUi.NumberedList -> NumberedList(block = block, textStyle = textStyle)
            is BlockUi.Code -> CodeBlock(
                block = block,
                copied = block.key == copiedBlock,
                onCopy = { onCopyCode(block.key) },
                compact = compact,
            )
        }
    }
}

@Composable
private fun Paragraph(block: BlockUi.Paragraph, textStyle: TextStyle) {
    val style = textStyle.copy(lineBreak = LineBreak.Paragraph)
    if (!block.caret) {
        Text(text = block.text, style = style, color = ChatTheme.colors.text.primary)
        return
    }
    val alpha by caretAlpha(StreamingCaretBlinkMillis)
    val caret = caretStyle(alpha)
    Text(
        text = buildAnnotatedString {
            append(block.text)
            withStyle(caret) { append(Caret) }
        },
        style = style,
        color = ChatTheme.colors.text.primary,
    )
}

@Composable
private fun NumberedList(block: BlockUi.NumberedList, textStyle: TextStyle) {
    val number = textStyle.copy(fontFamily = ChatTheme.typography.input.fontFamily)
    Column(verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xxSmall)) {
        for (item in block.items) {
            Row(horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.medium)) {
                Text(
                    text = item.number,
                    modifier = Modifier.alignByBaseline(),
                    style = number,
                    color = ChatTheme.colors.text.tertiary,
                )
                Text(
                    text = item.text,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                    style = textStyle.copy(lineBreak = LineBreak.Paragraph),
                    color = ChatTheme.colors.text.primary,
                )
            }
        }
    }
}

/** The prototype's `thinking`: a lone accent caret, blinking every 0.8 s. */
@Composable
internal fun ThinkingCaret() {
    val alpha by caretAlpha(ThinkingCaretBlinkMillis)
    Text(
        text = Caret,
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha }
            .clearAndSetSemantics {},
        style = ChatTheme.typography.labelMedium,
        color = ChatTheme.colors.accent.primary,
    )
}
