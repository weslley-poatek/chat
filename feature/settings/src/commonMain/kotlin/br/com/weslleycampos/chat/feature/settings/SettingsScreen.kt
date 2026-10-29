package br.com.weslleycampos.chat.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatIconButton
import br.com.weslleycampos.chat.core.ui.components.ChatToast
import br.com.weslleycampos.chat.core.ui.icons.ChatIcons
import br.com.weslleycampos.chat.data.auth.AuthUser
import br.com.weslleycampos.chat.data.auth.SignInMethod
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import br.com.weslleycampos.chat.data.settings.models.Account
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.data.settings.models.ProviderKey
import br.com.weslleycampos.chat.feature.settings.resources.FeatureSettingsRes
import br.com.weslleycampos.chat.feature.settings.resources.settings_account
import br.com.weslleycampos.chat.feature.settings.resources.settings_api_keys
import br.com.weslleycampos.chat.feature.settings.resources.settings_api_keys_note
import br.com.weslleycampos.chat.feature.settings.resources.settings_back
import br.com.weslleycampos.chat.feature.settings.resources.settings_title
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_recent_login
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_key_stored
import br.com.weslleycampos.chat.feature.settings.resources.settings_voice
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The prototype's `margin:20px 0 6px` above the "api keys" label. */
private val GroupGap = 20.dp

/** The prototype's toast sits 84 px over the bottom inset. */
private val ToastBottomOffset = 84.dp

/**
 * The Settings frame (frames.md §2.5), reduced to what the user kept: the bar, the "account" group,
 * the "api keys" group, the "voice" group and the version footer, inside the safe area. The groups
 * scroll under the bottom inset and above the keyboard; the toast floats over their bottom.
 *
 * Settings shows no conversation, so it wears the root's accent.
 *
 * @param draft What is typed in the open edit row, shown masked.
 * @param appVersion The installed app's version, for the footer.
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    draft: String,
    appVersion: AppVersion,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onKeyRowClick: (ModelProvider) -> Unit,
    onDraftChange: (String) -> Unit,
    onSaveKey: () -> Unit,
    onRemoveKey: () -> Unit,
    onLanguagesClick: (VoiceLanguages) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Below the keyboard while it shows, else the navigation bar or home indicator.
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    val enabled = !uiState.deleting
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = ChatTheme.colors.surface.primary)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(enabled = enabled, onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = ChatTheme.spacing.xxLarge,
                        end = ChatTheme.spacing.xxLarge,
                        bottom = bottomInset + ChatTheme.spacing.large,
                    ),
            ) {
                uiState.account?.let { account ->
                    GroupLabel(
                        text = stringResource(FeatureSettingsRes.string.settings_account),
                        modifier = Modifier.padding(top = ChatTheme.spacing.small, bottom = ChatTheme.spacing.xSmall),
                    )
                    AccountGroup(
                        account = account,
                        deleteHint = uiState.deleteHint,
                        enabled = enabled,
                        onSignOut = onSignOut,
                        onDeleteAccount = onDeleteAccount,
                    )
                }
                if (uiState.keys.isNotEmpty()) {
                    KeysLabelRow(modifier = Modifier.padding(top = GroupGap, bottom = ChatTheme.spacing.xSmall))
                    KeysGroup(
                        rows = uiState.keyRows,
                        draft = draft,
                        enabled = enabled,
                        onRowClick = onKeyRowClick,
                        onDraftChange = onDraftChange,
                        onSave = onSaveKey,
                        onRemove = onRemoveKey,
                    )
                }
                uiState.languages?.let { languages ->
                    GroupLabel(
                        text = stringResource(FeatureSettingsRes.string.settings_voice),
                        modifier = Modifier.padding(top = GroupGap, bottom = ChatTheme.spacing.xSmall),
                    )
                    VoiceGroup(languages = languages, enabled = enabled, onLanguagesClick = onLanguagesClick)
                }
                Text(
                    text = settingsFooter(appVersion),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ChatTheme.spacing.xxxLarge),
                    style = ChatTheme.typography.labelSmall,
                    color = ChatTheme.colors.text.tertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        ChatToast(
            message = uiState.toast?.let { stringResource(it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + ToastBottomOffset),
        )
    }
}

/** The back chevron and the title (frame lines 349–352), with no hairline under it. */
@Composable
private fun SettingsTopBar(enabled: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChatTheme.sizes.xxxLarge)
            .padding(horizontal = ChatTheme.spacing.xSmall),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatIconButton(
            onClick = onBack,
            contentDescription = stringResource(FeatureSettingsRes.string.settings_back),
            enabled = enabled,
        ) {
            Icon(painter = painterResource(ChatIcons.Back), contentDescription = null)
        }
        Text(
            text = stringResource(FeatureSettingsRes.string.settings_title),
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = ChatTheme.typography.headlineLarge,
            color = ChatTheme.colors.text.primary,
        )
    }
}

/** A group's lowercase mono label, such as "account". */
@Composable
private fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = ChatTheme.typography.labelSmall,
        color = ChatTheme.colors.text.tertiary,
    )
}

/** "api keys" and, on the right, "encrypted on device · never sent in plain text". */
@Composable
private fun KeysLabelRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.large),
    ) {
        GroupLabel(text = stringResource(FeatureSettingsRes.string.settings_api_keys))
        Text(
            text = stringResource(FeatureSettingsRes.string.settings_api_keys_note),
            modifier = Modifier.weight(1f),
            style = ChatTheme.typography.labelSmall,
            color = ChatTheme.colors.text.tertiary,
            textAlign = TextAlign.End,
        )
    }
}

/** The prototype's profile, "Ana Ribeiro", signed in with Google. */
private val PreviewAccount = Account(
    user = AuthUser(id = "preview", displayName = "Ana Ribeiro", email = "ana.ribeiro@example.dev", photoUrl = null),
    signInMethods = listOf(SignInMethod.GOOGLE),
).toUi()

/** An email + password account, which has no display name. */
private val PreviewEmailAccount = Account(
    user = AuthUser(id = "preview", displayName = null, email = "you@example.dev", photoUrl = null),
    signInMethods = listOf(SignInMethod.EMAIL_PASSWORD),
).toUi()

/** Fuelix's key stored, shown masked as the repository reads it. */
private val PreviewKeys = listOf(ProviderKey(provider = ModelProvider.FUELIX, masked = "sk-test…4f2a"))

/** Fuelix with no key yet. */
private val PreviewNoKeys = listOf(ProviderKey(provider = ModelProvider.FUELIX, masked = null))

/** A fake key, never a real one: the previews show it masked as the screen does. */
private const val PreviewDraft = "sk-test-0000000000"

private val PreviewVersion = AppVersion(name = "1.0", code = "1", platform = "ios")

private val PreviewState = SettingsUiState(
    account = PreviewAccount,
    keys = PreviewKeys,
    languages = VoiceLanguages(VoiceLanguage.PORTUGUESE_BRAZIL),
)

data class SettingsPreviewCase(
    val name: String,
    val uiState: SettingsUiState,
    val draft: String,
    val darkTheme: Boolean,
) {
    val displayName: String get() = "$name · ${if (darkTheme) "Dark" else "Light"}"
}

/** Every state of the Settings frame, in light and dark. */
class SettingsScreenPreviewParameterProvider : PreviewParameterProvider<SettingsPreviewCase> {
    private val states = listOf(
        Triple("Signed in", PreviewState, ""),
        Triple("Email account", PreviewState.copy(account = PreviewEmailAccount), ""),
        Triple("Delete armed", PreviewState.copy(deleteArmed = true), ""),
        Triple("Deleting", PreviewState.copy(deleting = true), ""),
        Triple(
            "Delete refused",
            PreviewState.copy(toast = FeatureSettingsRes.string.settings_toast_delete_recent_login),
            "",
        ),
        Triple("No key", PreviewState.copy(keys = PreviewNoKeys), ""),
        Triple("Editing a key", PreviewState.copy(editing = ModelProvider.FUELIX), ""),
        Triple("Adding a key", PreviewState.copy(keys = PreviewNoKeys, editing = ModelProvider.FUELIX), PreviewDraft),
        Triple("Key stored", PreviewState.copy(toast = FeatureSettingsRes.string.settings_toast_key_stored), ""),
        Triple("English voice", PreviewState.copy(languages = VoiceLanguages(VoiceLanguage.ENGLISH_US)), ""),
        Triple(
            "Both languages",
            PreviewState.copy(languages = VoiceLanguages(VoiceLanguage.PORTUGUESE_BRAZIL, bilingual = true)),
            "",
        ),
    )

    override val values: Sequence<SettingsPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for ((name, state, draft) in states) {
                yield(SettingsPreviewCase(name = name, uiState = state, draft = draft, darkTheme = dark))
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).displayName
}

@Preview(name = "Settings", widthDp = 402, heightDp = 874)
@Composable
private fun SettingsScreenPreview(
    @PreviewParameter(SettingsScreenPreviewParameterProvider::class) case: SettingsPreviewCase,
) {
    ChatTheme(darkTheme = case.darkTheme) {
        SettingsScreen(
            uiState = case.uiState,
            draft = case.draft,
            appVersion = PreviewVersion,
            onBack = {},
            onSignOut = {},
            onDeleteAccount = {},
            onKeyRowClick = {},
            onDraftChange = {},
            onSaveKey = {},
            onRemoveKey = {},
            onLanguagesClick = {},
        )
    }
}
