package br.com.weslleycampos.chat.feature.chat.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.feature.chat.HairlineWidth
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_compare
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_compare_on
import org.jetbrains.compose.resources.stringResource

// The Model picker's compare controls: the header's button and the hint under it.

/**
 * The compare button (frame line 484), the prototype's `toggleCompare`: "compare" in `--fg2` inside a
 * `--line` border while off, "compare · on" filled with the accent while [on]; 30 tall, mono 11,
 * radius 4, over a target the header's full 44 tall. Screen readers hear a "compare" switch.
 */
@Composable
fun ModelPickerCompareButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(FeatureChatRes.string.model_picker_compare)
    val label = if (on) stringResource(FeatureChatRes.string.model_picker_compare_on) else description
    val shape = ChatTheme.shapes.compact
    val accent = ChatTheme.colors.accent.primary
    val border = if (on) accent else ChatTheme.colors.border.primary
    Box(
        modifier = modifier
            .heightIn(min = ChatTheme.sizes.xxLarge)
            .toggleable(
                value = on,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = { onClick() },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(ChatTheme.sizes.large)
                .background(color = if (on) accent else Color.Transparent, shape = shape)
                .border(width = HairlineWidth, color = border, shape = shape)
                .padding(horizontal = ChatTheme.spacing.medium),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                modifier = Modifier.clearAndSetSemantics {},
                style = ChatTheme.typography.labelSmall,
                color = if (on) ChatTheme.colors.text.onAccent else ChatTheme.colors.text.secondary,
            )
        }
    }
}

/** The compare hint under the header (frame line 487): mono 11 `--fg3`, `padding:0 16px 8px`. */
@Composable
internal fun ModelPickerCompareHint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = ChatTheme.spacing.xxLarge,
            end = ChatTheme.spacing.xxLarge,
            bottom = ChatTheme.spacing.small,
        ),
        style = ChatTheme.typography.labelSmall,
        color = ChatTheme.colors.text.tertiary,
    )
}
