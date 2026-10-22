package br.com.weslleycampos.chat.feature.chat

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.theme.ChatSyntaxColors

// The Chat frame's small pieces that :core:ui has no component for.

/** The streaming caret, the prototype's `▍`. */
internal const val Caret = "▍"

/** The prototype's `blink … steps(1)` periods: after streamed text, and alone while thinking. */
internal const val StreamingCaretBlinkMillis = 1000
internal const val ThinkingCaretBlinkMillis = 800

private const val CaretShownFraction = 0.5f
private val CaretStep = Easing { fraction -> if (fraction < CaretShownFraction) 0f else 1f }

/** Every divider of the frame: `1px solid var(--line)`. */
internal val HairlineWidth = 1.dp

/**
 * The caret's opacity: 1 for the first half of each [periodMillis], 0 for the second. A static
 * preview draws it shown.
 */
@Composable
internal fun caretAlpha(periodMillis: Int): State<Float> {
    val transition = rememberInfiniteTransition()
    val alpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = if (LocalInspectionMode.current) 1f else 0f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = periodMillis, easing = CaretStep)),
    )
    return alpha
}

/** The caret as a run of text, in the accent at [alpha]. */
@Composable
internal fun caretStyle(alpha: Float): SpanStyle = SpanStyle(
    color = ChatTheme.colors.accent.primary.copy(alpha = alpha),
    fontFamily = ChatTheme.typography.labelMedium.fontFamily,
)

/** The prototype's model and meta dots: a [size] circle, the accent unless told otherwise. */
@Composable
internal fun Dot(
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = ChatTheme.colors.accent.primary,
    shape: Shape = ChatTheme.shapes.pill,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color = color, shape = shape),
    )
}

/** A hairline along the top edge, as `border-top`. */
internal fun Modifier.topHairline(color: Color): Modifier = drawBehind {
    val stroke = HairlineWidth.toPx()
    drawLine(
        color = color,
        start = Offset(0f, stroke / 2),
        end = Offset(size.width, stroke / 2),
        strokeWidth = stroke,
    )
}

/** A hairline along the bottom edge, as `border-bottom`. */
internal fun Modifier.bottomHairline(color: Color): Modifier = drawBehind {
    val stroke = HairlineWidth.toPx()
    drawLine(
        color = color,
        start = Offset(0f, size.height - stroke / 2),
        end = Offset(size.width, size.height - stroke / 2),
        strokeWidth = stroke,
    )
}

/** The colour of a code run: `var(--tk-<kind>)`, or the block's text colour when plain. */
internal fun TokenKind?.color(syntax: ChatSyntaxColors): Color = when (this) {
    TokenKind.Comment -> syntax.comment
    TokenKind.String -> syntax.string
    TokenKind.Number -> syntax.number
    TokenKind.Type -> syntax.type
    TokenKind.Keyword -> syntax.keyword
    TokenKind.Function -> syntax.function
    null -> Color.Unspecified
}
