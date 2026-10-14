package br.com.weslleycampos.chat.feature.auth.signin

import androidx.compose.runtime.Composable

/**
 * Opens the platform's Google account picker: Credential Manager on Android, `GIDSignIn` on iOS.
 * A picker the user dismisses calls neither callback.
 */
@Composable
internal expect fun rememberGoogleSignInLauncher(
    onToken: (idToken: String, accessToken: String?) -> Unit,
    onError: (Throwable) -> Unit,
): () -> Unit
