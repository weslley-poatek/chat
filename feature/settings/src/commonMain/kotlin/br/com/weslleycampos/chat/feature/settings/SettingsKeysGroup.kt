package br.com.weslleycampos.chat.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatButton
import br.com.weslleycampos.chat.core.ui.components.ChatButtonSize
import br.com.weslleycampos.chat.core.ui.components.ChatButtonVariant
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.feature.settings.resources.FeatureSettingsRes
import br.com.weslleycampos.chat.feature.settings.resources.settings_key_input_description
import br.com.weslleycampos.chat.feature.settings.resources.settings_key_not_set
import br.com.weslleycampos.chat.feature.settings.resources.settings_key_remove
import br.com.weslleycampos.chat.feature.settings.resources.settings_key_save
import org.jetbrains.compose.resources.stringResource

/** A key row's dot: 7 px, filled when a key is stored, else a 1.5 px ring. */
private val KeyDotSize = 7.dp
private val KeyDotStroke = 1.5.dp

/** A key row's provider name, a fixed 88 px wide so the masked keys line up. */
private val ProviderNameWidth = 88.dp

/** The edit row's left inset, under the masked key: `padding:0 12px 12px 34px`. */
private val EditRowStart = 34.dp

/** The selection's highlight over the accent, as `ChatTextField`'s. */
private const val SelectionAlpha = 0.4f

/**
 * The "api keys" group (frame lines 386–404): one row per provider and, under the one being edited,
 * its edit row with the [draft].
 */
@Composable
internal fun KeysGroup(
    rows: List<KeyRowUi>,
    draft: String,
    enabled: Boolean,
    onRowClick: (ModelProvider) -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = ChatTheme.colors.border.primary
    SettingsGroup(modifier = modifier) {
        rows.forEachIndexed { index, row ->
            // Every row but the last has the hairline under it; the card's border closes the last.
            Column(modifier = if (index < rows.lastIndex) Modifier.bottomHairline(line) else Modifier) {
                KeyRow(row = row, enabled = enabled, onClick = { onRowClick(row.provider) })
                if (row.editing) {
                    KeyEditRow(
                        row = row,
                        draft = draft,
                        onDraftChange = onDraftChange,
                        onSave = onSave,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }
}

/** The dot, the provider, the masked key or "not set", and "edit" / "add". A tap opens or closes its edit row. */
@Composable
private fun KeyRow(row: KeyRowUi, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tappable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ChatTheme.spacing.large, vertical = KeyRowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyDot(hasKey = row.hasKey)
        Text(
            text = row.name,
            modifier = Modifier.width(ProviderNameWidth),
            style = ChatTheme.typography.bodyMedium,
            color = ChatTheme.colors.text.primary,
            maxLines = 1,
        )
        Text(
            text = row.masked ?: stringResource(FeatureSettingsRes.string.settings_key_not_set),
            modifier = Modifier.weight(1f),
            style = ChatTheme.typography.labelMedium,
            color = ChatTheme.colors.text.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        row.action?.let { action ->
            Text(
                text = stringResource(action),
                style = ChatTheme.typography.labelSmall,
                color = ChatTheme.colors.text.tertiary,
            )
        }
    }
}

/** The prototype's `padding:11px 12px` on a key row. */
private val KeyRowVerticalPadding = 11.dp

/** Green and filled while a key is stored (the prototype's positive green), else a hollow `--fg3` ring. */
@Composable
private fun KeyDot(hasKey: Boolean) {
    val shape = ChatTheme.shapes.pill
    Box(
        modifier = Modifier
            .size(KeyDotSize)
            .then(
                if (hasKey) {
                    Modifier.background(color = ChatTheme.colors.status.success, shape = shape)
                } else {
                    Modifier.border(width = KeyDotStroke, color = ChatTheme.colors.text.tertiary, shape = shape)
                },
            ),
    )
}

/**
 * The edit row: the input, focused as it opens, "save" and, for a stored key, "remove". The key is
 * typed masked, on a password keyboard with no autocorrect, suggestions or capitalisation, so neither
 * the screen nor the keyboard keeps it; screen readers hear only that it is a password field.
 */
@Composable
private fun KeyEditRow(
    row: KeyRowUi,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = EditRowStart, end = ChatTheme.spacing.large, bottom = ChatTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyInput(
            value = draft,
            placeholder = row.placeholder,
            description = stringResource(FeatureSettingsRes.string.settings_key_input_description, row.name),
            onValueChange = onDraftChange,
            onDone = onSave,
            modifier = Modifier.weight(1f),
        )
        ChatButton(
            onClick = onSave,
            modifier = Modifier.height(ChatTheme.sizes.xLarge),
            variant = ChatButtonVariant.Primary,
            size = ChatButtonSize.Small,
        ) {
            Text(text = stringResource(FeatureSettingsRes.string.settings_key_save))
        }
        if (row.removable) {
            Box(
                modifier = Modifier
                    .height(ChatTheme.sizes.xLarge)
                    .tappable(enabled = true, onClick = onRemove)
                    .padding(horizontal = ChatTheme.spacing.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(FeatureSettingsRes.string.settings_key_remove),
                    style = ChatTheme.typography.labelSmall,
                    color = ChatTheme.colors.status.danger,
                )
            }
        }
    }
}

/**
 * The key input (frame line 397): 36 tall, `--bg2`, a `--fg3` border, radius 4, mono 12 and the
 * provider's placeholder. Done on the keyboard saves.
 */
@Composable
private fun KeyInput(
    value: String,
    placeholder: String,
    description: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val inspecting = LocalInspectionMode.current
    LaunchedEffect(focusRequester) {
        if (!inspecting) focusRequester.requestFocus()
    }
    val shape = ChatTheme.shapes.compact
    val textStyle = ChatTheme.typography.labelMedium.copy(color = ChatTheme.colors.text.primary)
    val accent = ChatTheme.colors.accent.primary
    val selectionColors = TextSelectionColors(
        handleColor = accent,
        backgroundColor = accent.copy(alpha = SelectionAlpha),
    )
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .height(ChatTheme.sizes.xLarge)
                .focusRequester(focusRequester)
                .semantics {
                    contentDescription = description
                    password()
                },
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            cursorBrush = SolidColor(ChatTheme.colors.text.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .background(color = ChatTheme.colors.surface.secondary, shape = shape)
                        .border(width = HairlineWidth, color = ChatTheme.colors.text.tertiary, shape = shape)
                        .padding(horizontal = ChatTheme.spacing.medium),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = ChatTheme.colors.text.tertiary,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
