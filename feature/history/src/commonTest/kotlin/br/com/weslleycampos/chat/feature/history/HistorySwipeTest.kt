package br.com.weslleycampos.chat.feature.history

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The prototype's swipe on its 1 px = 1 dp scale: open is 192 to the left. */
private const val Open = -192f

class HistorySwipeTest {
    @Test
    fun anOpenRowSitsItsThreeActionsWidthToTheLeft() {
        assertEquals(64.dp, SwipeActionWidth)
        assertEquals(192.dp, SwipeOpenDistance)
        assertEquals(3, HistoryRowAction.entries.size)
    }

    @Test
    fun aDragMovesTheRowBetweenClosedAndOpenAndNoFurther() {
        assertEquals(-50f, clampSwipeOffset(-50f, Open))
        assertEquals(Open, clampSwipeOffset(-250f, Open))
        assertEquals(0f, clampSwipeOffset(30f, Open))
        // An open row dragged right from -192 closes as far as the finger goes.
        assertEquals(-120f, clampSwipeOffset(Open + 72f, Open))
    }

    @Test
    fun aReleasePastHalfTheActionsOpensTheRowAndOneShortOfItClosesIt() {
        assertTrue(swipeSettlesOpen(-97f, Open))
        assertTrue(swipeSettlesOpen(Open, Open))
        assertFalse(swipeSettlesOpen(-96f, Open))
        assertFalse(swipeSettlesOpen(-6f, Open))
        assertFalse(swipeSettlesOpen(0f, Open))
    }

    @Test
    fun aRowRestsOpenOrClosed() {
        assertEquals(Open, restingSwipeOffset(open = true, openOffset = Open))
        assertEquals(0f, restingSwipeOffset(open = false, openOffset = Open))
    }

    @Test
    fun aReleaseSettlesOverTheDesigns180MsCurve() {
        assertEquals(180, SwipeSettleMillis)
        assertEquals(CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f), SwipeSettleEasing)
    }
}
