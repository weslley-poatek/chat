package br.com.weslleycampos.chat.feature.history

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.theme.ChatColors
import br.com.weslleycampos.chat.feature.history.resources.FeatureHistoryRes
import br.com.weslleycampos.chat.feature.history.resources.history_compared_with
import br.com.weslleycampos.chat.feature.history.resources.history_section_pinned
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** The prototype's row padding, `11px 16px 10px`, and line 2's `margin-top:3px`. */
private val RowPaddingTop = 11.dp
private val RowPaddingBottom = 10.dp
private val RowLineGap = 3.dp

/** The row's 6 px model dot. */
private val ModelDotSize = 6.dp

/** Every divider of the frame: `1px solid var(--line)`. */
private val HairlineWidth = 1.dp

/** The prototype's pinned mark. */
private const val PinnedMark = "▲"

/**
 * One History row (frame lines 276–294): the conversation over its three actions. A horizontal
 * drag slides it left, up to the actions' width, and on release it settles open past half of that
 * or closed, over 180 ms; a vertical drag scrolls the list instead. A drag never counts as a tap,
 * so the release that opens a row neither opens the conversation nor closes the row again.
 *
 * [open] is where the row rests; it settles there whenever it changes, so opening another row or
 * picking an action closes this one. The actions are composed only while the row is moved, and
 * screen readers reach the same three as custom actions on the row.
 */
@Composable
internal fun HistoryRow(
    row: HistoryRowUi,
    open: Boolean,
    onClick: () -> Unit,
    onSwipeStart: () -> Unit,
    onSwipeSettle: (open: Boolean) -> Unit,
    onAction: (HistoryRowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openOffset = with(LocalDensity.current) { -SwipeOpenDistance.toPx() }
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    var offset by remember { mutableFloatStateOf(restingSwipeOffset(open, openOffset)) }
    val dragState = rememberDraggableState { delta -> offset = clampSwipeOffset(offset + delta, openOffset) }
    val moved by remember { derivedStateOf { offset < 0f } }
    LaunchedEffect(open, openOffset) {
        dragState.settle(from = offset, to = restingSwipeOffset(open, openOffset)) { offset = it }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            if (moved) RowActions(row = row, onAction = onAction, modifier = Modifier.matchParentSize())
            RowContent(
                row = row,
                onClick = onClick,
                onAction = onAction,
                modifier = Modifier
                    // Mirrored in right-to-left, where the actions sit on the left.
                    .offset { IntOffset(x = offset.roundToInt(), y = 0) }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        reverseDirection = rightToLeft,
                        onDragStarted = { onSwipeStart() },
                        onDragStopped = {
                            val opens = swipeSettlesOpen(offset, openOffset)
                            onSwipeSettle(opens)
                            dragState.settle(from = offset, to = restingSwipeOffset(opens, openOffset)) { offset = it }
                        },
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HairlineWidth)
                .background(ChatTheme.colors.border.primary),
        )
    }
}

/**
 * Animates the row from [from] to [to] as the prototype's release does. It holds the drag's mutex,
 * so a new drag stops it where it is and takes over.
 */
private suspend fun DraggableState.settle(from: Float, to: Float, onValue: (Float) -> Unit) {
    if (from == to) return
    drag(MutatePriority.Default) {
        animate(initialValue = from, targetValue = to, animationSpec = SwipeSettleSpec) { value, _ -> onValue(value) }
    }
}

/**
 * The title line and the model line, in the screen's colour over the actions. The model line's
 * extra, "⇄ gpt-5", reads "compared with gpt-5" to screen readers.
 */
@Composable
private fun RowContent(
    row: HistoryRowUi,
    onClick: () -> Unit,
    onAction: (HistoryRowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val labels = row.actions.map { stringResource(it.label) }
    val pinnedLabel = stringResource(FeatureHistoryRes.string.history_section_pinned)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ChatTheme.colors.surface.primary)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics {
                customActions = row.actions.mapIndexed { index, action ->
                    CustomAccessibilityAction(label = labels[index]) {
                        onAction(action.action)
                        true
                    }
                }
            }
            .padding(
                start = ChatTheme.spacing.xxLarge,
                end = ChatTheme.spacing.xxLarge,
                top = RowPaddingTop,
                bottom = RowPaddingBottom,
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.pinned) {
                Text(
                    text = PinnedMark,
                    modifier = Modifier.semantics { contentDescription = pinnedLabel },
                    style = ChatTheme.typography.caption,
                    color = ChatTheme.colors.text.tertiary,
                )
            }
            Text(
                text = row.title,
                style = ChatTheme.typography.bodyLarge,
                color = if (pressed) ChatTheme.colors.accent.primary else ChatTheme.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.padding(top = RowLineGap),
            horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(ModelDotSize)
                    .background(
                        color = row.palette.colors(ChatTheme.colors.isDark).accent.primary,
                        shape = ChatTheme.shapes.pill,
                    ),
            )
            Text(
                text = row.modelShortName,
                style = ChatTheme.typography.labelSmall,
                color = ChatTheme.colors.text.tertiary,
                maxLines = 1,
            )
            row.extra?.let { extra ->
                val description = stringResource(
                    FeatureHistoryRes.string.history_compared_with,
                    row.comparedWith.orEmpty(),
                )
                Text(
                    text = extra,
                    modifier = Modifier.semantics { contentDescription = description },
                    style = ChatTheme.typography.labelSmall,
                    color = ChatTheme.colors.text.tertiary,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(text = row.time, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
        }
    }
}

/** "pin" / "unpin", "archive" and "delete", each 64 wide and the row's full height, at its end. */
@Composable
private fun RowActions(row: HistoryRowUi, onAction: (HistoryRowAction) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.End) {
        for (action in row.actions) {
            val (container, content) = historyRowActionColors(action.action, ChatTheme.colors)
            Box(
                modifier = Modifier
                    .width(SwipeActionWidth)
                    .fillMaxHeight()
                    .background(container)
                    .clickable(role = Role.Button) { onAction(action.action) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(action.label), style = ChatTheme.typography.labelSmall, color = content)
            }
        }
    }
}

/**
 * An action's fill and text: "pin" `--bg3` with `--fg`, "archive" `--fg3` with `--bg`, "delete"
 * `--danger` with `--bg`.
 */
internal fun historyRowActionColors(action: HistoryRowAction, colors: ChatColors): Pair<Color, Color> = when (action) {
    HistoryRowAction.TogglePin -> colors.surface.tertiary to colors.text.primary
    HistoryRowAction.Archive -> colors.text.tertiary to colors.surface.primary
    HistoryRowAction.Delete -> colors.status.danger to colors.surface.primary
}

/** A group label, "pinned", "today", …: mono 11 `--fg3`, `padding:12px 16px 4px`. */
@Composable
internal fun HistoryGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ChatTheme.spacing.xxLarge,
                end = ChatTheme.spacing.xxLarge,
                top = ChatTheme.spacing.large,
                bottom = ChatTheme.spacing.xxSmall,
            )
            .semantics { heading() },
        style = ChatTheme.typography.labelSmall,
        color = ChatTheme.colors.text.tertiary,
    )
}

/** "no matches for “…”" and the empty state: mono 12 `--fg3`, centred, `padding:32px 16px`. */
@Composable
internal fun HistoryMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ChatTheme.spacing.xxLarge, vertical = ChatTheme.spacing.xxLarge * 2),
        style = ChatTheme.typography.labelMedium,
        color = ChatTheme.colors.text.tertiary,
        textAlign = TextAlign.Center,
    )
}

/** The footer hint: mono 11 `--fg3`, centred, `padding:14px 16px`. */
@Composable
internal fun HistoryFooterHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ChatTheme.spacing.xxLarge, vertical = ChatTheme.spacing.xLarge),
        style = ChatTheme.typography.labelSmall,
        color = ChatTheme.colors.text.tertiary,
        textAlign = TextAlign.Center,
    )
}
