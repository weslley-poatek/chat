@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.feature.auth.signin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import swiftPMImport.Chat.feature.feature.auth.GIDSignIn
import swiftPMImport.Chat.feature.feature.auth.kGIDSignInErrorCodeCanceled

@Composable
internal actual fun rememberGoogleSignInLauncher(
    onToken: (idToken: String, accessToken: String?) -> Unit,
    onError: (Throwable) -> Unit,
): () -> Unit {
    val viewController = LocalUIViewController.current
    val currentOnToken by rememberUpdatedState(onToken)
    val currentOnError by rememberUpdatedState(onError)
    return remember(viewController) {
        {
            // iOSApp sets the configuration from Firebase's client ID; GIDSignIn raises an exception without one.
            if (GIDSignIn.sharedInstance.configuration == null) {
                currentOnError(IllegalStateException("GIDSignIn has no configuration: set it in iOSApp.init()"))
            } else {
                GIDSignIn.sharedInstance.signInWithPresentingViewController(viewController) { result, error ->
                    val user = result?.user
                    val idToken = user?.idToken?.tokenString
                    val dismissedByUser = error?.code == kGIDSignInErrorCodeCanceled
                    if (user != null && idToken != null) {
                        currentOnToken(idToken, user.accessToken.tokenString)
                    } else if (!dismissedByUser) {
                        currentOnError(IllegalStateException(error?.localizedDescription ?: "Google sign-in failed"))
                    }
                }
            }
        }
    }
}
