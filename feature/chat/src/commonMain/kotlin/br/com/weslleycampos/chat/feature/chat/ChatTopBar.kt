package br.com.weslleycampos.chat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatIconButton
import br.com.weslleycampos.chat.core.ui.icons.ChatIcons
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The prototype's top bar height. */
private val TopBarHeight = 48.dp

/** The chip's 7 px dot. */
private val ChipDotSize = 7.dp

/** The chip's `▾`. */
private const val Dropdown = "▾"

/**
 * Back, the model chip and a 44 px spacer, over a hairline (frame lines 107–115). The thread is read
 * from History or Voice, so the back chevron takes the History button's place, and new sessions
 * start on Voice, so the spacer takes New chat's and keeps the chip as wide as the frame's.
 */
@Composable
internal fun ChatTopBar(
    chipLabel: String,
    onBack: () -> Unit,
    onModelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .bottomHairline(ChatTheme.colors.border.primary)
            .padding(horizontal = ChatTheme.spacing.xSmall),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatIconButton(onClick = onBack, contentDescription = stringResource(FeatureChatRes.string.chat_back)) {
            Icon(painter = painterResource(ChatIcons.Back), contentDescription = null)
        }
        ModelChip(label = chipLabel, onClick = onModelClick, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(ChatTheme.sizes.xxLarge))
    }
}

/**
 * The model chip: the glowing accent dot, [label] ("Claude Sonnet 4.5", or "Code reviewer ·
 * sonnet-4.5" while a persona answers) and `▾`. It opens the Model picker.
 */
@Composable
private fun ModelChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = ChatTheme.shapes.control
    val accent = ChatTheme.colors.accent.primary
    Row(
        modifier = modifier
            .height(ChatTheme.sizes.xLarge)
            .background(
                color = if (pressed) ChatTheme.colors.surface.tertiary else ChatTheme.colors.surface.secondary,
                shape = shape,
            )
            .border(width = HairlineWidth, color = ChatTheme.colors.border.primary, shape = shape)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = ChatTheme.spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(
            size = ChipDotSize,
            modifier = Modifier.dropShadow(
                shape = ChatTheme.shapes.pill,
                shadow = Shadow(radius = ChatTheme.elevation.glow, color = accent),
            ),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = ChatTheme.typography.labelMedium,
            color = ChatTheme.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = Dropdown,
            modifier = Modifier.clearAndSetSemantics {},
            style = ChatTheme.typography.caption,
            color = ChatTheme.colors.text.tertiary,
        )
    }
}
