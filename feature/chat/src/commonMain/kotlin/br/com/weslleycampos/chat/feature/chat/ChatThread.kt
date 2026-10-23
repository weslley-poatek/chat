package br.com.weslleycampos.chat.feature.chat

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_message_hide_actions
import br.com.weslleycampos.chat.feature.chat.resources.chat_message_show_actions
import br.com.weslleycampos.chat.feature.chat.resources.chat_meta_interrupted
import br.com.weslleycampos.chat.feature.chat.resources.chat_meta_user
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

/** Each message's `fadeIn 0.2s ease-out`. */
private const val MessageFadeInMillis = 200

/**
 * The thread (frame lines 119 and 142–217): the messages, oldest first, the [selectedMessage]'s
 * action row and the read-aloud bar under the reply [readAloud] reads. Whenever the messages
 * change, a send and every streaming tick included, or the thread gets shorter under the keyboard,
 * it snaps to its end, as the prototype's `scrollTop = scrollHeight`.
 */
@Composable
internal fun ChatThread(
    messages: List<MessageUi>,
    copiedBlock: BlockKey?,
    onCopyCode: (BlockKey) -> Unit,
    selectedMessage: Long?,
    copiedMessage: Long?,
    readAloud: ReadAloudUi?,
    actions: MessageActionCallbacks,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LaunchedEffect(listState, messages) { listState.scrollToEnd() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }.drop(1).collect { listState.scrollToEnd() }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = ChatTheme.spacing.xSmall, bottom = ChatTheme.spacing.large),
    ) {
        items(items = messages, key = { it.id }, contentType = { it::class }) { message ->
            ChatMessage(
                message = message,
                copiedBlock = copiedBlock,
                onCopyCode = onCopyCode,
                selected = message.id == selectedMessage,
                copied = message.id == copiedMessage,
                reading = readAloud?.takeIf { it.messageId == message.id },
                actions = actions,
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(durationMillis = MessageFadeInMillis, easing = EaseOut),
                    placementSpec = null,
                    fadeOutSpec = null,
                ),
            )
        }
    }
}

/** Shows the end of the last item, however tall it has grown. */
private suspend fun LazyListState.scrollToEnd() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    scrollToItem(lastIndex)
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return
    val hidden = last.offset + last.size + info.afterContentPadding - info.viewportEndOffset
    if (hidden > 0) scrollBy(hidden.toFloat())
}

/**
 * One message: its meta row and its bubble or blocks, which a tap selects (the prototype's
 * `m.select`, "Show actions" to screen readers), then a reply's read-aloud bar while it is read
 * and the action row while the message is selected.
 */
@Composable
private fun ChatMessage(
    message: MessageUi,
    copiedBlock: BlockKey?,
    onCopyCode: (BlockKey) -> Unit,
    selected: Boolean,
    copied: Boolean,
    reading: ReadAloudUi?,
    actions: MessageActionCallbacks,
    modifier: Modifier = Modifier,
) {
    val tap = Modifier.messageTap(
        enabled = message.selectable,
        label = stringResource(
            when {
                selected -> FeatureChatRes.string.chat_message_hide_actions
                else -> FeatureChatRes.string.chat_message_show_actions
            },
        ),
        onClick = { actions.onSelect(message.id) },
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ChatTheme.spacing.xxLarge,
                end = ChatTheme.spacing.xxLarge,
                top = ChatTheme.spacing.medium,
                bottom = ChatTheme.spacing.xxxSmall,
            ),
    ) {
        when (message) {
            is MessageUi.User -> {
                MetaRow(text = stringResource(FeatureChatRes.string.chat_meta_user, message.time), assistant = false)
                UserBubble(text = message.text, modifier = tap)
            }
            is MessageUi.Assistant -> {
                val interrupted = if (message.interrupted) {
                    stringResource(FeatureChatRes.string.chat_meta_interrupted)
                } else {
                    null
                }
                MetaRow(
                    text = assistantMeta(
                        modelShortName = message.modelShortName,
                        personaName = message.personaName,
                        time = message.time,
                        latency = message.latency,
                        interruptedLabel = interrupted,
                    ),
                    assistant = true,
                )
                AssistantBody(message = message, copiedBlock = copiedBlock, onCopyCode = onCopyCode, modifier = tap)
                if (reading != null) {
                    ReadAloudBar(reading = reading, onPlayPause = actions.onPlayPause, onStop = actions.onStopReading)
                }
            }
        }
        if (selected && message.selectable) MessageActionRow(message = message, copied = copied, actions = actions)
    }
}

/** A tap that selects the message, labelled for screen readers; none on a reply on its way. */
private fun Modifier.messageTap(enabled: Boolean, label: String, onClick: () -> Unit): Modifier =
    if (enabled) {
        clickable(
            interactionSource = null,
            indication = null,
            onClickLabel = label,
            onClick = onClick,
        )
    } else {
        this
    }

/** The meta row: a 6 px accent circle for a reply, a 6 px square for the user, then the meta text. */
@Composable
private fun MetaRow(text: String, assistant: Boolean) {
    Row(
        modifier = Modifier.padding(bottom = ChatTheme.spacing.xSmall),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (assistant) {
            Dot(size = ChatTheme.spacing.xSmall)
        } else {
            Dot(
                size = ChatTheme.spacing.xSmall,
                color = ChatTheme.colors.text.tertiary,
                shape = ChatTheme.shapes.marker,
            )
        }
        Text(text = text, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
    }
}

@Composable
private fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .background(color = ChatTheme.colors.surface.secondary, shape = ChatTheme.shapes.bubble)
            .padding(horizontal = ChatTheme.spacing.large, vertical = ChatTheme.spacing.medium),
        style = ChatTheme.typography.bodyLarge,
        color = ChatTheme.colors.text.primary,
    )
}

@Composable
private fun AssistantBody(
    message: MessageUi.Assistant,
    copiedBlock: BlockKey?,
    onCopyCode: (BlockKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.medium)) {
        for (block in message.blocks) {
            when (block) {
                is BlockUi.Paragraph -> Paragraph(block)
                is BlockUi.NumberedList -> NumberedList(block)
                is BlockUi.Code -> CodeBlock(
                    block = block,
                    copied = block.key == copiedBlock,
                    onCopy = { onCopyCode(block.key) },
                )
            }
        }
        if (message.thinking) ThinkingCaret()
    }
}

@Composable
private fun Paragraph(block: BlockUi.Paragraph) {
    val style = ChatTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph)
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
private fun NumberedList(block: BlockUi.NumberedList) {
    val text = ChatTheme.typography.bodyLarge
    val number = text.copy(fontFamily = ChatTheme.typography.input.fontFamily)
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
                    style = text.copy(lineBreak = LineBreak.Paragraph),
                    color = ChatTheme.colors.text.primary,
                )
            }
        }
    }
}

/** The prototype's `thinking`: a lone accent caret, blinking every 0.8 s. */
@Composable
private fun ThinkingCaret() {
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
