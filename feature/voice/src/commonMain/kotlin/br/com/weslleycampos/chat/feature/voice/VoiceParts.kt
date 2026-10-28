package br.com.weslleycampos.chat.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatIconButton
import br.com.weslleycampos.chat.core.ui.icons.ChatIcons
import br.com.weslleycampos.chat.feature.voice.resources.FeatureVoiceRes
import br.com.weslleycampos.chat.feature.voice.resources.voice_end
import br.com.weslleycampos.chat.feature.voice.resources.voice_end_description
import br.com.weslleycampos.chat.feature.voice.resources.voice_history
import br.com.weslleycampos.chat.feature.voice.resources.voice_model
import br.com.weslleycampos.chat.feature.voice.resources.voice_mute_description
import br.com.weslleycampos.chat.feature.voice.resources.voice_settings
import br.com.weslleycampos.chat.feature.voice.resources.voice_text
import br.com.weslleycampos.chat.feature.voice.resources.voice_text_description
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The bar's `height:48px`. */
private val TopBarHeight = 48.dp

/** The chip: `height:32px`, its dot 7 px. */
private val ChipHeight = 32.dp
private val ChipDotSize = 7.dp

/** A 1 px CSS border. */
private val HairlineWidth = 1.dp

/** Mute and "Aa" are 56 px circles, end a 72 px one. */
private val SideControlSize = 56.dp
private val EndControlSize = 72.dp

/** The controls' `padding:0 24px calc(var(--sb) + 18px)`. */
private val ControlsSidePadding = 24.dp
private val ControlsBottomPadding = 18.dp

/** "Aa" is mono 13. */
private val TextGlyphFontSize = 13.sp
private const val TextGlyph = "Aa"

/**
 * History, the model chip and Settings (frame lines 448–453, the back chevron replaced by History
 * since Voice is home, and the 44 px spacer by Settings), the chip centred between them, with no
 * hairline under the bar.
 */
@Composable
internal fun VoiceTopBar(
    chipLabel: String,
    onHistoryClick: () -> Unit,
    onModelClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = ChatTheme.spacing.xSmall),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatIconButton(
            onClick = onHistoryClick,
            contentDescription = stringResource(FeatureVoiceRes.string.voice_history),
        ) {
            Icon(painter = painterResource(ChatIcons.Menu), contentDescription = null)
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            VoiceModelChip(label = chipLabel, onClick = onModelClick)
        }
        ChatIconButton(
            onClick = onSettingsClick,
            contentDescription = stringResource(FeatureVoiceRes.string.voice_settings),
        ) {
            Icon(painter = painterResource(ChatIcons.Settings), contentDescription = null)
        }
    }
}

/** The model chip: the 7 px accent dot and the short name, "sonnet-4.5", with no "▾" and no glow. */
@Composable
private fun VoiceModelChip(label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = ChatTheme.shapes.control
    val description = stringResource(FeatureVoiceRes.string.voice_model, label)
    Row(
        modifier = Modifier
            .height(ChipHeight)
            .background(
                color = if (pressed) ChatTheme.colors.surface.tertiary else ChatTheme.colors.surface.secondary,
                shape = shape,
            )
            .border(width = HairlineWidth, color = ChatTheme.colors.border.primary, shape = shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = ChatTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ChipDotSize)
                .background(color = ChatTheme.colors.accent.primary, shape = ChatTheme.shapes.pill),
        )
        Text(
            text = label,
            style = ChatTheme.typography.labelMedium,
            color = ChatTheme.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Mute, end and "Aa", each a circle over its mono 10 label (frame lines 459–472). Mute is `--bg2`
 * with a `--fg2` microphone, and while muted `--fg` with a `--bg` crossed-out one; end is `--fg` with
 * a `--bg` "×"; "Aa" is `--bg2` with `--fg2` text.
 */
@Composable
internal fun VoiceControls(
    muted: Boolean,
    muteLabel: String,
    onToggleMute: () -> Unit,
    onEnd: () -> Unit,
    onTextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChatTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ControlsSidePadding, end = ControlsSidePadding, bottom = ControlsBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xxxLarge, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val muteDescription = stringResource(FeatureVoiceRes.string.voice_mute_description)
        VoiceControl(label = muteLabel) {
            VoiceCircle(
                size = SideControlSize,
                container = if (muted) colors.text.primary else colors.surface.secondary,
                tint = if (muted) colors.surface.primary else colors.text.secondary,
                bordered = true,
                modifier = Modifier
                    .toggleable(
                        value = muted,
                        interactionSource = null,
                        indication = null,
                        role = Role.Switch,
                        onValueChange = { onToggleMute() },
                    )
                    .semantics { contentDescription = muteDescription },
            ) {
                Icon(
                    painter = painterResource(if (muted) ChatIcons.MicOff else ChatIcons.Mic),
                    contentDescription = null,
                )
            }
        }
        val endDescription = stringResource(FeatureVoiceRes.string.voice_end_description)
        VoiceControl(label = stringResource(FeatureVoiceRes.string.voice_end)) {
            VoiceCircle(
                size = EndControlSize,
                container = colors.text.primary,
                tint = colors.surface.primary,
                bordered = false,
                modifier = Modifier
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onEnd)
                    .semantics { contentDescription = endDescription },
            ) {
                Icon(painter = painterResource(ChatIcons.Close), contentDescription = null)
            }
        }
        val textDescription = stringResource(FeatureVoiceRes.string.voice_text_description)
        VoiceControl(label = stringResource(FeatureVoiceRes.string.voice_text)) {
            VoiceCircle(
                size = SideControlSize,
                container = colors.surface.secondary,
                tint = colors.text.secondary,
                bordered = true,
                modifier = Modifier
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onTextClick)
                    .clearAndSetSemantics { contentDescription = textDescription },
            ) {
                Text(
                    text = TextGlyph,
                    style = ChatTheme.typography.labelMedium.copy(fontSize = TextGlyphFontSize),
                    color = colors.text.secondary,
                )
            }
        }
    }
}

/** A control over its label; the label repeats what the button says, so a screen reader skips it. */
@Composable
private fun VoiceControl(label: String, button: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xSmall),
    ) {
        button()
        Text(
            text = label,
            modifier = Modifier.clearAndSetSemantics {},
            style = ChatTheme.typography.caption,
            color = ChatTheme.colors.text.tertiary,
        )
    }
}

/** A [size] circle in [container], its icon tinted [tint], with a `--line` hairline when [bordered]. */
@Composable
private fun VoiceCircle(
    size: Dp,
    container: Color,
    tint: Color,
    bordered: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val shape = ChatTheme.shapes.pill
    Box(
        modifier = Modifier
            .size(size)
            .background(color = container, shape = shape)
            .then(
                if (bordered) {
                    Modifier.border(width = HairlineWidth, color = ChatTheme.colors.border.primary, shape = shape)
                } else {
                    Modifier
                },
            )
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint, content = icon)
    }
}
