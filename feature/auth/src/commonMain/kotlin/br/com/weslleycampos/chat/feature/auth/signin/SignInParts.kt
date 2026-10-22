package br.com.weslleycampos.chat.feature.auth.signin

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.feature.auth.resources.FeatureAuthRes
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_app_name
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_or
import org.jetbrains.compose.resources.stringResource

// The Auth frame's small pieces that :core:ui has no component for.

/** The wordmark's caret, the prototype's `▍` after "ctx". */
private const val Caret = "▍"

/** The prototype's `blink 1.1s steps(1)`: shown for the first half of each period, hidden for the second. */
private const val CaretBlinkMillis = 1100
private const val CaretShownFraction = 0.5f
private val CaretStep = Easing { fraction -> if (fraction < CaretShownFraction) 0f else 1f }

/** The prototype's text links have no pressed state; this is the touch feedback they get instead. */
private const val PressedLinkAlpha = 0.6f

/** "Chat" in the prototype's logo style, with the accent caret blinking after it. */
@Composable
internal fun Wordmark(modifier: Modifier = Modifier) {
    val caretAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = CaretBlinkMillis, easing = CaretStep)),
    )
    Row(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = stringResource(FeatureAuthRes.string.sign_in_app_name),
            style = ChatTheme.typography.displaySmall,
            color = ChatTheme.colors.text.primary,
        )
        Text(
            text = Caret,
            modifier = Modifier
                .graphicsLayer { alpha = caretAlpha }
                .clearAndSetSemantics {},
            style = ChatTheme.typography.displaySmall,
            color = ChatTheme.colors.accent.primary,
        )
    }
}

@Composable
internal fun OrDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ChatTheme.spacing.xxLarge + ChatTheme.spacing.xxxSmall, bottom = ChatTheme.spacing.xLarge),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Rule(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(FeatureAuthRes.string.sign_in_or),
            style = ChatTheme.typography.labelSmall,
            color = ChatTheme.colors.text.tertiary,
        )
        Rule(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Rule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(color = ChatTheme.colors.border.primary),
    )
}

/** The prototype's "show" / "hide": a 36 tall mono button inside the field's trailing slot. */
@Composable
internal fun PasswordToggle(label: String, description: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .height(ChatTheme.sizes.xLarge)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .alpha(if (pressed) PressedLinkAlpha else 1f)
            .padding(horizontal = ChatTheme.spacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label)
    }
}

/** A text link: the prototype's borderless button, given a 44 tall touch target. */
@Composable
internal fun AuthLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = ChatTheme.typography.bodySmall,
    color: Color = ChatTheme.colors.text.primary,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = modifier
            .heightIn(min = ChatTheme.sizes.xxLarge)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .alpha(if (pressed) PressedLinkAlpha else 1f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = style, color = color)
    }
}
