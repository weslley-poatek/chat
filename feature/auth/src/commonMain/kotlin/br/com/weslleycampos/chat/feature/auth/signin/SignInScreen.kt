package br.com.weslleycampos.chat.feature.auth.signin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatButton
import br.com.weslleycampos.chat.core.ui.components.ChatButtonVariant
import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.core.ui.components.ChatVoiceSphere
import br.com.weslleycampos.chat.feature.auth.resources.FeatureAuthRes
import br.com.weslleycampos.chat.feature.auth.resources.ic_google
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_app_name
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_google
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_google_network_error
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SignInScreen(
    uiState: SignInUiState,
    onGoogleSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appName = stringResource(FeatureAuthRes.string.sign_in_app_name)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = ChatTheme.colors.surface.primary)
            .safeDrawingPadding()
            .padding(horizontal = ChatTheme.spacing.xxLarge, vertical = ChatTheme.spacing.xxxLarge),
    ) {
        ChatVoiceSphere(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics {
                    contentDescription = appName
                    heading()
                },
            phase = if (uiState.loading) ChatVoicePhase.Thinking else ChatVoicePhase.Listening,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            uiState.error?.let { error ->
                Text(
                    text = stringResource(error),
                    modifier = Modifier
                        .padding(bottom = ChatTheme.spacing.large)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = ChatTheme.typography.bodyMedium,
                    color = ChatTheme.colors.status.danger,
                    textAlign = TextAlign.Center,
                )
            }

            ChatButton(
                onClick = onGoogleSignInClick,
                modifier = Modifier.fillMaxWidth(),
                variant = ChatButtonVariant.Secondary,
                loading = uiState.loading,
            ) {
                Image(
                    painter = painterResource(FeatureAuthRes.drawable.ic_google),
                    contentDescription = null,
                    modifier = Modifier.size(ChatTheme.sizes.xSmall),
                )
                Text(text = stringResource(FeatureAuthRes.string.sign_in_google))
            }
        }
    }
}

class SignInUiStatePreviewParameterProvider : PreviewParameterProvider<SignInUiState> {
    override val values: Sequence<SignInUiState> = sequenceOf(
        SignInUiState(),
        SignInUiState(loading = true),
        SignInUiState(error = FeatureAuthRes.string.sign_in_google_network_error),
    )

    override fun getDisplayName(index: Int): String = listOf("Idle", "Loading", "Error")[index]
}

@Preview(name = "Sign in")
@Composable
private fun SignInScreenPreview(
    @PreviewParameter(SignInUiStatePreviewParameterProvider::class) uiState: SignInUiState,
) {
    ChatTheme {
        SignInScreen(uiState = uiState, onGoogleSignInClick = {})
    }
}
