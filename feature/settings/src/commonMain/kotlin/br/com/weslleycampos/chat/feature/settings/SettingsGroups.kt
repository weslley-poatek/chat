package br.com.weslleycampos.chat.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.feature.settings.resources.FeatureSettingsRes
import br.com.weslleycampos.chat.feature.settings.resources.settings_delete_account
import br.com.weslleycampos.chat.feature.settings.resources.settings_sign_in_methods
import br.com.weslleycampos.chat.feature.settings.resources.settings_sign_out
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The groups' 1 px border and the hairlines between their rows. */
internal val HairlineWidth = 1.dp

/** The profile row's avatar: 40 x 40, mono 13 600. */
private val AvatarSize = 40.dp
private val InitialsFontSize = 13.sp

/** A group's card: `border:1px solid var(--line); border-radius:6px; overflow:hidden`. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = ChatTheme.shapes.control
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(width = HairlineWidth, color = ChatTheme.colors.border.primary, shape = shape),
    ) {
        content()
    }
}

/**
 * The "account" group (frame lines 356–378), signed in: the profile row, "Sign-in methods" with its
 * summary, "Sign out", and "Delete account" in the danger colour with its [deleteHint]. The
 * prototype's "Biometric unlock" is not built, and neither "edit ›" nor the methods' expanded rows,
 * since nothing here edits the profile or links a method.
 */
@Composable
internal fun AccountGroup(
    account: AccountUi,
    deleteHint: StringResource?,
    enabled: Boolean,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val line = ChatTheme.colors.border.primary
    SettingsGroup(modifier = modifier) {
        ProfileRow(account = account, modifier = Modifier.bottomHairline(line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bottomHairline(line)
                .semantics(mergeDescendants = true) {}
                .padding(ChatTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(FeatureSettingsRes.string.settings_sign_in_methods),
                modifier = Modifier.weight(1f),
                style = ChatTheme.typography.bodyMedium,
                color = ChatTheme.colors.text.primary,
            )
            Text(
                text = account.signInMethods,
                style = ChatTheme.typography.labelSmall,
                color = ChatTheme.colors.text.tertiary,
            )
        }
        ActionRow(
            label = stringResource(FeatureSettingsRes.string.settings_sign_out),
            color = ChatTheme.colors.text.primary,
            enabled = enabled,
            onClick = onSignOut,
            modifier = Modifier.bottomHairline(line),
        )
        ActionRow(
            label = stringResource(FeatureSettingsRes.string.settings_delete_account),
            color = ChatTheme.colors.status.danger,
            enabled = enabled,
            onClick = onDeleteAccount,
            hint = deleteHint?.let { stringResource(it) },
        )
    }
}

/** The avatar with the initials, the name and the email in mono, ellipsized. */
@Composable
private fun ProfileRow(account: AccountUi, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(ChatTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .background(color = ChatTheme.colors.surface.tertiary, shape = ChatTheme.shapes.control),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = account.initials,
                style = ChatTheme.typography.labelMedium.copy(
                    fontSize = InitialsFontSize,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = ChatTheme.colors.text.secondary,
                maxLines = 1,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = ChatTheme.typography.titleLarge,
                color = ChatTheme.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            account.email?.let { email ->
                Text(
                    text = email,
                    style = ChatTheme.typography.labelSmall,
                    color = ChatTheme.colors.text.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A full-width 14 px row that acts, such as "Sign out", with an optional mono hint on its right. */
@Composable
private fun ActionRow(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .tappable(enabled = enabled, onClick = onClick)
            .padding(ChatTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = ChatTheme.typography.bodyMedium, color = color)
        if (hint != null) {
            Text(text = hint, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
        }
    }
}

/**
 * A tap target with no ripple, as the prototype's plain buttons and History's rows: what it does
 * shows in the screen's state instead (the hint, the edit row, the next screen).
 */
internal fun Modifier.tappable(enabled: Boolean, onClick: () -> Unit): Modifier = clickable(
    interactionSource = null,
    indication = null,
    enabled = enabled,
    role = Role.Button,
    onClick = onClick,
)

/** A hairline along the bottom edge, as `border-bottom:1px solid var(--line)`. */
internal fun Modifier.bottomHairline(color: Color): Modifier = drawBehind {
    val stroke = HairlineWidth.toPx()
    drawLine(
        color = color,
        start = Offset(0f, size.height - stroke / 2),
        end = Offset(size.width, size.height - stroke / 2),
        strokeWidth = stroke,
    )
}
