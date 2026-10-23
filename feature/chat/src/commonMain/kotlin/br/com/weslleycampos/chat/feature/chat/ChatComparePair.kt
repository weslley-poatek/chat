package br.com.weslleycampos.chat.feature.chat

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatButton
import br.com.weslleycampos.chat.core.ui.components.ChatButtonSize
import br.com.weslleycampos.chat.core.ui.components.ChatButtonVariant
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_compare_continue
import br.com.weslleycampos.chat.feature.chat.resources.chat_compare_continue_description
import br.com.weslleycampos.chat.feature.chat.resources.chat_meta_interrupted
import org.jetbrains.compose.resources.stringResource

/** A card's `width:86%`, of the carousel inside its 16 px gutters. */
internal const val CompareCardWidthFraction = 0.86f

/** A card header's 7 px dot. */
private val CardDotSize = 7.dp

/** A fling this fast or faster, per second, goes on to the other card; a slower one settles on the nearer. */
private val CompareFlingThreshold = 400.dp

/**
 * A compare pair (frame lines 186–206): the meta row, "sonnet-4.5 ⇄ gpt-5 · 14:05", over the
 * carousel of its two cards. It spans the thread's whole width, as the prototype's
 * `margin:0 -16px`, and keeps the 16 dp gutters inside: the meta row is inset by them, and the
 * cards scroll under them to the screen's edges.
 */
@Composable
internal fun ComparePairBody(
    pair: MessageUi.ComparePair,
    copiedBlock: BlockKey?,
    onCopyCode: (BlockKey) -> Unit,
    onContinueWith: (pairId: Long, side: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MetaRow(
            text = compareMeta(pair),
            assistant = true,
            modifier = Modifier.padding(horizontal = ChatTheme.spacing.xxLarge),
        )
        CompareCarousel(
            pair = pair,
            copiedBlock = copiedBlock,
            onCopyCode = onCopyCode,
            onContinueWith = { side -> onContinueWith(pair.id, side) },
        )
    }
}

/**
 * The prototype's `overflow-x:auto; scroll-snap-type:x mandatory` row: the two cards side by side,
 * 8 dp apart, each [compareCardWidth] wide, so the first shows whole with the second peeking at the
 * right and a swipe settles on the second with the first peeking at the left. Both are as tall as
 * the taller one, as the flex row's `stretch`. To screen readers it is a collection of two, each
 * card's header a heading; moving into the second card scrolls it into view.
 */
@Composable
private fun CompareCarousel(
    pair: MessageUi.ComparePair,
    copiedBlock: BlockKey?,
    onCopyCode: (BlockKey) -> Unit,
    onContinueWith: (side: Int) -> Unit,
) {
    val gutter = ChatTheme.spacing.xxLarge
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = compareCardWidth(viewport = maxWidth, gutter = gutter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(state = scroll, flingBehavior = rememberCompareSnapFling(scroll))
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = pair.sides.size) }
                .padding(horizontal = gutter)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
        ) {
            for (side in pair.sides) {
                key(side.id) {
                    CompareCard(
                        side = side,
                        copiedBlock = copiedBlock,
                        onCopyCode = onCopyCode,
                        onContinue = { onContinueWith(side.index) },
                        modifier = Modifier
                            .width(cardWidth)
                            .fillMaxHeight()
                            .semantics {
                                collectionItemInfo = CollectionItemInfo(
                                    rowIndex = 0,
                                    rowSpan = 1,
                                    columnIndex = side.index,
                                    columnSpan = 1,
                                )
                            },
                    )
                }
            }
        }
    }
}

/**
 * One card (frame lines 189–203), in its own model's accent, which its dot and caret wear: the
 * header, the blocks at 14 px, the lone caret while it thinks, and "continue with this →" once it
 * is done. `--bg` inside a `--line` border, `padding:10px 12px`, 10 apart.
 */
@Composable
private fun CompareCard(
    side: CompareSideUi,
    copiedBlock: BlockKey?,
    onCopyCode: (BlockKey) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChatTheme(palette = side.palette, darkTheme = ChatTheme.colors.isDark) {
        val shape = ChatTheme.shapes.control
        Column(
            modifier = modifier
                .background(color = ChatTheme.colors.surface.primary, shape = shape)
                .border(width = HairlineWidth, color = ChatTheme.colors.border.primary, shape = shape)
                .padding(horizontal = ChatTheme.spacing.large, vertical = ChatTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.medium),
        ) {
            CardHeader(side)
            ReplyBlocks(
                blocks = side.blocks,
                copiedBlock = copiedBlock,
                onCopyCode = onCopyCode,
                textStyle = ChatTheme.typography.bodyMedium,
                compact = true,
            )
            if (side.thinking) ThinkingCaret()
            if (side.done) ContinueButton(modelName = side.modelName, onClick = onContinue)
        }
    }
}

/** The 7 dp dot in the card's accent and its label, mono 11 `--fg2`: a heading to screen readers. */
@Composable
private fun CardHeader(side: CompareSideUi) {
    val interrupted = stringResource(FeatureChatRes.string.chat_meta_interrupted)
    val label = compareSideLabel(side = side, interruptedLabel = interrupted)
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(size = CardDotSize)
        Text(text = label, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.secondary)
    }
}

/**
 * "continue with this →", the prototype's `p.keep` button: the small outlined button, `--fg2` and
 * `--fg` while pressed, at the card's start. Screen readers hear which model it continues with.
 */
@Composable
private fun ContinueButton(modelName: String, onClick: () -> Unit) {
    val description = stringResource(FeatureChatRes.string.chat_compare_continue_description, modelName)
    ChatButton(onClick = onClick, variant = ChatButtonVariant.Outlined, size = ChatButtonSize.Small) {
        Text(
            text = stringResource(FeatureChatRes.string.chat_compare_continue),
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        )
    }
}

/** Settles the carousel on one card or the other, as `scroll-snap-type:x mandatory` does. */
@Composable
private fun rememberCompareSnapFling(scroll: ScrollState): FlingBehavior {
    val threshold = with(LocalDensity.current) { CompareFlingThreshold.toPx() }
    val provider = remember(scroll, threshold) {
        object : SnapLayoutInfoProvider {
            // No approach: from wherever the finger lets go, it snaps straight to a card.
            override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f

            override fun calculateSnapOffset(velocity: Float): Float {
                val current = scroll.value.toFloat()
                val target = compareSnapTarget(
                    current = current,
                    max = scroll.maxValue.toFloat(),
                    velocity = velocity,
                    flingThreshold = threshold,
                )
                return target - current
            }
        }
    }
    return rememberSnapFlingBehavior(provider)
}

/**
 * A card's width in a carousel [viewport] wide with [gutter]s at both ends: 86 % of what is between
 * them, as the prototype's `width:86%` inside `padding:0 16px`. On a 402 wide screen it is 318, so
 * 60 of the other card peeks past it.
 */
fun compareCardWidth(viewport: Dp, gutter: Dp): Dp =
    ((viewport - gutter * 2) * CompareCardWidthFraction).coerceAtLeast(0.dp)

/**
 * Where the carousel settles, from [current] in `0..max`: at `0`, the first card, or at [max], the
 * second, since the second card's start is past the end of the scroll. A fling faster than
 * [flingThreshold] goes on in its direction (positive scrolls towards the second card); a slower
 * one or a release settles on the nearer card.
 */
fun compareSnapTarget(current: Float, max: Float, velocity: Float, flingThreshold: Float): Float = when {
    velocity > flingThreshold -> max
    velocity < -flingThreshold -> 0f
    current * 2 >= max -> max
    else -> 0f
}
