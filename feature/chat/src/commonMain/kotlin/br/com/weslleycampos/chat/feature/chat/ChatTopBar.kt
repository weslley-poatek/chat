package br.com.weslleycampos.chat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import br.com.weslleycampos.chat.feature.chat.resources.chat_history
import br.com.weslleycampos.chat.feature.chat.resources.chat_new_chat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The prototype's top bar height. */
private val TopBarHeight = 48.dp

/** The chip's 7 px dot. */
private val ChipDotSize = 7.dp

/** The chip's `▾`. */
private const val Dropdown = "▾"

/** History, the model chip and New chat, over a hairline (frame lines 107–115). */
@Composable
internal fun ChatTopBar(
    modelName: String,
    onHistoryClick: () -> Unit,
    onModelClick: () -> Unit,
    onNewChat: () -> Unit,
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
        ChatIconButton(
            onClick = onHistoryClick,
            contentDescription = stringResource(FeatureChatRes.string.chat_history),
        ) {
            Icon(painter = painterResource(ChatIcons.Menu), contentDescription = null)
        }
        ModelChip(name = modelName, onClick = onModelClick, modifier = Modifier.weight(1f))
        ChatIconButton(onClick = onNewChat, contentDescription = stringResource(FeatureChatRes.string.chat_new_chat)) {
            Icon(painter = painterResource(ChatIcons.NewChat), contentDescription = null)
        }
    }
}

/** The model chip: the glowing accent dot, the model's full name and `▾`. */
@Composable
private fun ModelChip(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            text = name,
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
