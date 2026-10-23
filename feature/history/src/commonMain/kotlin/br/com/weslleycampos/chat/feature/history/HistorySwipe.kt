package br.com.weslleycampos.chat.feature.history

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A row's swipe, the prototype's `swipeMove` / `swipeEnd` (795–797): pure, so the tests pin the
// clamp and the snap. Offsets are in pixels, negative to the left, the way the row moves.

/** Each action behind a row, "pin", "archive" and "delete", is 64 px wide. */
val SwipeActionWidth: Dp = 64.dp

/** An open row sits this far to the left: its three actions, 192 px. */
val SwipeOpenDistance: Dp = SwipeActionWidth * HistoryRowAction.entries.size

/** The prototype's `transform .18s cubic-bezier(.2,.8,.2,1)`, for a row settling open or closed. */
internal const val SwipeSettleMillis = 180
internal val SwipeSettleEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
internal val SwipeSettleSpec: AnimationSpec<Float> = tween(
    durationMillis = SwipeSettleMillis,
    easing = SwipeSettleEasing,
)

/**
 * `swipeMove`: where a dragged row is drawn, never right of closed (0) nor left of open
 * ([openOffset], negative).
 */
fun clampSwipeOffset(offset: Float, openOffset: Float): Float = offset.coerceIn(openOffset, 0f)

/** `swipeEnd`'s `dx < -96`: released past half of [openOffset], the row opens; otherwise it closes. */
fun swipeSettlesOpen(offset: Float, openOffset: Float): Boolean = offset < openOffset / 2

/** Where a row rests: [openOffset] while open, 0 while closed. */
fun restingSwipeOffset(open: Boolean, openOffset: Float): Float = if (open) openOffset else 0f
