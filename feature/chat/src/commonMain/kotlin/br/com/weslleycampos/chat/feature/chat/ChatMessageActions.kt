package br.com.weslleycampos.chat.feature.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatButton
import br.com.weslleycampos.chat.core.ui.components.ChatButtonSize
import br.com.weslleycampos.chat.core.ui.components.ChatButtonVariant
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_action_fork
import br.com.weslleycampos.chat.feature.chat.resources.chat_action_read_aloud
import br.com.weslleycampos.chat.feature.chat.resources.chat_action_retry
import br.com.weslleycampos.chat.feature.chat.resources.chat_code_copied
import br.com.weslleycampos.chat.feature.chat.resources.chat_code_copy
import br.com.weslleycampos.chat.feature.chat.resources.chat_fork_banner_dismiss
import br.com.weslleycampos.chat.feature.chat.resources.chat_read_pause
import br.com.weslleycampos.chat.feature.chat.resources.chat_read_play
import br.com.weslleycampos.chat.feature.chat.resources.chat_read_stop
import org.jetbrains.compose.resources.stringResource

/** The prototype's fork glyph. Screen readers skip it. */
internal const val ForkGlyph = "⑂"

private const val PlayGlyph = "▶"
private const val PauseGlyph = "❚❚"
private const val CloseGlyph = "×"

/** The action row's `fadeIn 0.15s ease-out`. */
private const val ActionRowFadeInMillis = 150

/** The read-aloud bar's 2 px track. */
private val ReadTrackHeight = 2.dp

/**
 * What a message does: a tap on it (the prototype's `m.select`), its action row, and the read-aloud
 * bar under a reply. Each takes the message's id where it needs one.
 */
@Immutable
data class MessageActionCallbacks(
    val onSelect: (Long) -> Unit = {},
    val onCopy: (Long) -> Unit = {},
    val onFork: (Long) -> Unit = {},
    val onReadAloud: (Long) -> Unit = {},
    val onRetry: (Long) -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onStopReading: () -> Unit = {},
)

/**
 * The action row under a selected message (frame lines 208–215), fading in over 150 ms: "copy" /
 * "copied", "⑂ fork here", and a reply's "read aloud" or a user message's "retry". It scrolls
 * itself into view, since it opens under the last message as often as not.
 */
@Composable
internal fun MessageActionRow(
    message: MessageUi,
    copied: Boolean,
    actions: MessageActionCallbacks,
    modifier: Modifier = Modifier,
) {
    val inspecting = LocalInspectionMode.current
    val alpha = remember { Animatable(if (inspecting) 1f else 0f) }
    LaunchedEffect(alpha) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = ActionRowFadeInMillis, easing = EaseOut),
        )
    }
    val reveal = rememberRevealOnAppear()
    Row(
        modifier = modifier
            .padding(top = ChatTheme.spacing.small)
            .bringIntoViewRequester(reveal)
            .graphicsLayer { this.alpha = alpha.value },
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xxSmall),
    ) {
        val copy = if (copied) FeatureChatRes.string.chat_code_copied else FeatureChatRes.string.chat_code_copy
        ActionButton(label = stringResource(copy), onClick = { actions.onCopy(message.id) }, announced = true)
        val fork = stringResource(FeatureChatRes.string.chat_action_fork)
        ActionButton(label = "$ForkGlyph $fork", description = fork, onClick = { actions.onFork(message.id) })
        when (message) {
            is MessageUi.Assistant -> ActionButton(
                label = stringResource(FeatureChatRes.string.chat_action_read_aloud),
                onClick = { actions.onReadAloud(message.id) },
            )
            is MessageUi.User -> ActionButton(
                label = stringResource(FeatureChatRes.string.chat_action_retry),
                onClick = { actions.onRetry(message.id) },
            )
        }
    }
}

/**
 * A small mono button of the row: 30 tall on `--bg2` with a `--line` border, its label `--fg2`,
 * `--fg` while pressed or hovered. Screen readers hear [description]; an [announced] label is read
 * again when it changes ("copied").
 */
@Composable
private fun ActionButton(label: String, onClick: () -> Unit, description: String = label, announced: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    ChatButton(
        onClick = onClick,
        modifier = if (announced) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
        variant = ChatButtonVariant.Secondary,
        size = ChatButtonSize.Small,
        interactionSource = source,
    ) {
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            color = if (pressed || hovered) ChatTheme.colors.text.primary else ChatTheme.colors.text.secondary,
        )
    }
}

/**
 * The read-aloud bar under a reply (frame lines 176–183): "▶" / "❚❚", the accent filling a 2 px
 * track, "0:08 / 0:17" and "×", 36 tall on `--bg2`. Its buttons span its height, so they are
 * bigger targets than the frame's 28 and 24 with their glyphs where the frame has them.
 */
@Composable
internal fun ReadAloudBar(
    reading: ReadAloudUi,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ChatTheme.shapes.control
    val reveal = rememberRevealOnAppear()
    Row(
        modifier = modifier
            .padding(top = ChatTheme.spacing.medium)
            .bringIntoViewRequester(reveal)
            .fillMaxWidth()
            .height(ChatTheme.sizes.xLarge)
            .background(color = ChatTheme.colors.surface.secondary, shape = shape)
            .border(width = HairlineWidth, color = ChatTheme.colors.border.primary, shape = shape)
            .padding(end = ChatTheme.spacing.xxxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            glyph = if (reading.playing) PauseGlyph else PlayGlyph,
            description = stringResource(
                if (reading.playing) FeatureChatRes.string.chat_read_pause else FeatureChatRes.string.chat_read_play,
            ),
            color = ChatTheme.colors.text.primary,
            onClick = onPlayPause,
        )
        Spacer(modifier = Modifier.width(ChatTheme.spacing.xSmall))
        ReadTrack(progress = reading.progress, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(ChatTheme.spacing.medium))
        Text(text = reading.label, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.secondary)
        Spacer(modifier = Modifier.width(ChatTheme.spacing.xxSmall))
        GlyphButton(
            glyph = CloseGlyph,
            description = stringResource(FeatureChatRes.string.chat_read_stop),
            color = ChatTheme.colors.text.tertiary,
            onClick = onStop,
        )
    }
}

/** The 2 px `--bg3` track with the accent filling [progress] of it. */
@Composable
private fun ReadTrack(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(ReadTrackHeight)
            .background(ChatTheme.colors.surface.tertiary)
            .progressSemantics(progress),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(ChatTheme.colors.accent.primary),
        )
    }
}

@Composable
private fun GlyphButton(glyph: String, description: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(ChatTheme.sizes.xLarge)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            modifier = Modifier.clearAndSetSemantics {},
            style = ChatTheme.typography.labelMedium,
            color = color,
        )
    }
}

/**
 * The fork banner under the top bar (frame line 117): "⑂ " and [text] in mono 11 `--fg2` on
 * `--bg2` over a hairline, and "×", a 44 wide target, which hides it.
 */
@Composable
internal fun ForkBanner(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(ChatTheme.colors.surface.secondary)
            .bottomHairline(ChatTheme.colors.border.primary)
            .padding(start = ChatTheme.spacing.xxLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$ForkGlyph $text",
            modifier = Modifier
                .weight(1f)
                .padding(vertical = ChatTheme.spacing.xSmall)
                .clearAndSetSemantics { contentDescription = text },
            style = ChatTheme.typography.labelSmall,
            color = ChatTheme.colors.text.secondary,
        )
        val dismiss = stringResource(FeatureChatRes.string.chat_fork_banner_dismiss)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = ChatTheme.sizes.xxLarge)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = dismiss },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = CloseGlyph,
                modifier = Modifier.clearAndSetSemantics {},
                style = ChatTheme.typography.labelSmall,
                color = ChatTheme.colors.text.tertiary,
            )
        }
    }
}

/**
 * Scrolls the thread to show what it is attached to when that first appears: an action row or a
 * read-aloud bar opened under the last message would otherwise start below the fold.
 */
@Composable
private fun rememberRevealOnAppear(): BringIntoViewRequester {
    val requester = remember { BringIntoViewRequester() }
    val inspecting = LocalInspectionMode.current
    LaunchedEffect(requester) {
        if (inspecting) return@LaunchedEffect
        // One frame on, so it has been laid out where the thread put it.
        withFrameNanos { }
        requester.bringIntoView()
    }
    return requester
}
