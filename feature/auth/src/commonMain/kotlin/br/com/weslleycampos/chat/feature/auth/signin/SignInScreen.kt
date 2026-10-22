package br.com.weslleycampos.chat.feature.auth.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatButton
import br.com.weslleycampos.chat.core.ui.components.ChatButtonVariant
import br.com.weslleycampos.chat.core.ui.components.ChatTextField
import br.com.weslleycampos.chat.feature.auth.resources.FeatureAuthRes
import br.com.weslleycampos.chat.feature.auth.resources.ic_google
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_back_link
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_back_to_sign_in
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_email_placeholder
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_invalid_email
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_short_password
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_wrong_credentials
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_forgot_link
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_google
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_google_authorizing
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_password_hide_description
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_password_show_description
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_sent_note
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_sent_to
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_sign_in_link
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_sign_up_hint
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_sign_up_link
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The prototype's `flex:1.2` under the form, against `flex:1` above the title. */
private const val BottomSpacerWeight = 1.2f

private val SentLineHeight = 1.6.em

/**
 * The Auth frame: Google and email + password sign in, account creation and the password reset,
 * one screen whose [SignInUiState.mode] picks the form. The column fills the safe area and
 * scrolls once the keyboard leaves it too short.
 */
@Composable
fun SignInScreen(
    uiState: SignInUiState,
    onGoogleSignInClick: () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onModeChange: (AuthMode) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(color = ChatTheme.colors.surface.primary)
            .safeDrawingPadding(),
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(
                    start = ChatTheme.spacing.xxxLarge,
                    end = ChatTheme.spacing.xxxLarge,
                    bottom = ChatTheme.spacing.xLarge,
                ),
        ) {
            AuthBar(
                showsBackToSignIn = uiState.showsBackToSignIn,
                onBackToSignIn = { onModeChange(AuthMode.SignIn) },
            )
            Spacer(modifier = Modifier.weight(1f))
            AuthHeader(title = stringResource(uiState.title), subtitle = uiState.subtitle?.let { stringResource(it) })
            if (uiState.showsSentConfirmation) {
                SentConfirmation(email = uiState.email.trim(), onBackToSignIn = { onModeChange(AuthMode.SignIn) })
            }
            if (uiState.showsGoogle) {
                GoogleSignIn(authorizing = uiState.busy == AuthBusy.Google, onClick = onGoogleSignInClick)
                OrDivider()
            }
            if (uiState.showsForm) {
                EmailForm(
                    uiState = uiState,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                    onSubmit = onSubmit,
                )
                AuthLinks(mode = uiState.mode, onModeChange = onModeChange)
            }
            Spacer(modifier = Modifier.weight(BottomSpacerWeight))
        }
    }
}

@Composable
private fun AuthBar(showsBackToSignIn: Boolean, onBackToSignIn: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ChatTheme.spacing.xSmall)
            .height(ChatTheme.sizes.xxLarge),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark()
        if (showsBackToSignIn) {
            AuthLink(
                text = stringResource(FeatureAuthRes.string.sign_in_back_link),
                onClick = onBackToSignIn,
                style = ChatTheme.typography.labelMedium,
                color = ChatTheme.colors.text.secondary,
            )
        }
    }
}

@Composable
private fun AuthHeader(title: String, subtitle: String?) {
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        style = ChatTheme.typography.displayLarge,
        color = ChatTheme.colors.text.primary,
    )
    Spacer(modifier = Modifier.height(ChatTheme.spacing.small))
    if (subtitle != null) {
        Text(
            text = subtitle,
            style = ChatTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Paragraph),
            color = ChatTheme.colors.text.secondary,
        )
    }
}

@Composable
private fun SentConfirmation(email: String, onBackToSignIn: () -> Unit) {
    val sentTo = stringResource(FeatureAuthRes.string.sign_in_sent_to, email)
    val emailStart = sentTo.lastIndexOf(email)
    val emailColor = ChatTheme.colors.text.primary
    val note = stringResource(FeatureAuthRes.string.sign_in_sent_note)
    val message = remember(sentTo, email, note, emailColor) {
        buildAnnotatedString {
            append(sentTo)
            if (email.isNotEmpty() && emailStart >= 0) {
                addStyle(style = SpanStyle(color = emailColor), start = emailStart, end = emailStart + email.length)
            }
            append("\n")
            append(note)
        }
    }
    Text(
        text = message,
        modifier = Modifier
            .padding(top = ChatTheme.spacing.xxxLarge)
            .fillMaxWidth()
            .border(width = 1.dp, color = ChatTheme.colors.border.primary, shape = ChatTheme.shapes.control)
            .padding(ChatTheme.spacing.xLarge)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = ChatTheme.typography.labelMedium.copy(lineHeight = SentLineHeight),
        color = ChatTheme.colors.text.secondary,
    )
    ChatButton(
        onClick = onBackToSignIn,
        modifier = Modifier
            .padding(top = ChatTheme.spacing.xLarge)
            .fillMaxWidth(),
    ) {
        Text(text = stringResource(FeatureAuthRes.string.sign_in_back_to_sign_in))
    }
}

@Composable
private fun GoogleSignIn(authorizing: Boolean, onClick: () -> Unit) {
    ChatButton(
        onClick = onClick,
        modifier = Modifier
            .padding(top = ChatTheme.spacing.xxxLarge)
            .fillMaxWidth(),
        variant = ChatButtonVariant.Secondary,
    ) {
        // The prototype's 22 × 22 badge, holding the Google logo instead of a mono "G"; the extra
        // end padding turns the row's 8 gap into the prototype's 12.
        Box(
            modifier = Modifier
                .padding(end = ChatTheme.spacing.xxSmall)
                .size(ChatTheme.sizes.small),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(FeatureAuthRes.drawable.ic_google),
                contentDescription = null,
                modifier = Modifier.size(ChatTheme.sizes.xSmall),
            )
        }
        Text(text = stringResource(FeatureAuthRes.string.sign_in_google))
        Spacer(modifier = Modifier.weight(1f))
        if (authorizing) {
            Text(
                text = stringResource(FeatureAuthRes.string.sign_in_google_authorizing),
                style = ChatTheme.typography.labelSmall,
                color = ChatTheme.colors.text.tertiary,
            )
        }
    }
}

@Composable
private fun EmailForm(
    uiState: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
) {
    val submitOnDone = KeyboardActions(onDone = { onSubmit() })
    val passwordContentType = if (uiState.mode == AuthMode.SignUp) ContentType.NewPassword else ContentType.Password
    Column(verticalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small)) {
        ChatTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            modifier = Modifier
                .fillMaxWidth()
                .contentType(ContentType.EmailAddress + ContentType.Username),
            placeholder = stringResource(FeatureAuthRes.string.sign_in_email_placeholder),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = if (uiState.showsPasswordField) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = submitOnDone,
        )
        if (uiState.showsPasswordField) {
            ChatTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .contentType(passwordContentType),
                placeholder = stringResource(uiState.passwordPlaceholder),
                trailingIcon = {
                    PasswordToggle(
                        label = stringResource(uiState.passwordToggleLabel),
                        description = stringResource(
                            if (uiState.showPassword) {
                                FeatureAuthRes.string.sign_in_password_hide_description
                            } else {
                                FeatureAuthRes.string.sign_in_password_show_description
                            },
                        ),
                        onClick = onPasswordVisibilityToggle,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = submitOnDone,
                visualTransformation = if (uiState.showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            )
        }
        uiState.error?.let { error ->
            Text(
                text = stringResource(error),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = ChatTheme.typography.labelMedium,
                color = ChatTheme.colors.status.danger,
            )
        }
        ChatButton(
            onClick = onSubmit,
            modifier = Modifier
                .padding(top = ChatTheme.spacing.xxSmall)
                .fillMaxWidth(),
            loading = uiState.busy == AuthBusy.Form,
        ) {
            Text(text = stringResource(uiState.cta))
        }
    }
}

@Composable
private fun AuthLinks(mode: AuthMode, onModeChange: (AuthMode) -> Unit) {
    if (mode == AuthMode.Forgot) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ChatTheme.spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode == AuthMode.SignIn) {
            AuthLink(
                text = stringResource(FeatureAuthRes.string.sign_in_forgot_link),
                onClick = { onModeChange(AuthMode.Forgot) },
                color = ChatTheme.colors.text.secondary,
            )
            AuthLink(
                text = stringResource(FeatureAuthRes.string.sign_in_sign_up_link),
                onClick = { onModeChange(AuthMode.SignUp) },
            )
        } else {
            Text(
                text = stringResource(FeatureAuthRes.string.sign_in_sign_up_hint),
                style = ChatTheme.typography.labelSmall,
                color = ChatTheme.colors.text.tertiary,
            )
            AuthLink(
                text = stringResource(FeatureAuthRes.string.sign_in_sign_in_link),
                onClick = { onModeChange(AuthMode.SignIn) },
            )
        }
    }
}

private const val PreviewEmail = "you@example.dev"
private const val PreviewPassword = "correct horse"
private const val PreviewShortPassword = "horse"

data class SignInPreviewCase(val name: String, val uiState: SignInUiState, val darkTheme: Boolean) {
    val displayName: String get() = "$name · ${if (darkTheme) "Dark" else "Light"}"
}

/** Every Auth state the prototype draws, in light and dark. */
class SignInScreenPreviewParameterProvider : PreviewParameterProvider<SignInPreviewCase> {
    private val filled = SignInUiState(email = PreviewEmail, password = PreviewPassword)
    private val states = listOf(
        "Sign in" to SignInUiState(),
        "Sign in · filled" to filled,
        "Sign in · password shown" to filled.copy(showPassword = true),
        "Sign in · Google authorizing" to SignInUiState(busy = AuthBusy.Google),
        "Sign in · busy" to filled.copy(busy = AuthBusy.Form),
        "Sign in · error" to filled.copy(
            password = PreviewShortPassword,
            error = FeatureAuthRes.string.sign_in_error_wrong_credentials,
        ),
        "Create account" to SignInUiState(mode = AuthMode.SignUp),
        "Create account · busy" to filled.copy(mode = AuthMode.SignUp, busy = AuthBusy.Form),
        "Create account · error" to filled.copy(
            mode = AuthMode.SignUp,
            password = PreviewShortPassword,
            error = FeatureAuthRes.string.sign_in_error_short_password,
        ),
        "Reset password" to SignInUiState(mode = AuthMode.Forgot, email = PreviewEmail),
        "Reset password · busy" to SignInUiState(mode = AuthMode.Forgot, email = PreviewEmail, busy = AuthBusy.Form),
        "Reset password · error" to SignInUiState(
            mode = AuthMode.Forgot,
            email = "you@example",
            error = FeatureAuthRes.string.sign_in_error_invalid_email,
        ),
        "Check your inbox" to SignInUiState(mode = AuthMode.Forgot, email = PreviewEmail, forgotSent = true),
    )

    override val values: Sequence<SignInPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for ((name, state) in states) {
                yield(SignInPreviewCase(name = name, uiState = state, darkTheme = dark))
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).displayName
}

@Preview(name = "Sign in", widthDp = 402, heightDp = 874)
@Composable
private fun SignInScreenPreview(
    @PreviewParameter(SignInScreenPreviewParameterProvider::class) case: SignInPreviewCase,
) {
    ChatTheme(darkTheme = case.darkTheme) {
        SignInScreen(
            uiState = case.uiState,
            onGoogleSignInClick = {},
            onEmailChange = {},
            onPasswordChange = {},
            onPasswordVisibilityToggle = {},
            onModeChange = {},
            onSubmit = {},
        )
    }
}
