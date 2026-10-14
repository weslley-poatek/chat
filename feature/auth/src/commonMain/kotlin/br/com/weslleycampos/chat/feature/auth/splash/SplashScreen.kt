package br.com.weslleycampos.chat.feature.auth.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import br.com.weslleycampos.chat.core.ui.ChatTheme

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(color = ChatTheme.colors.surface.primary))
}

@Preview(name = "Splash")
@Composable
private fun SplashScreenPreview() {
    ChatTheme {
        SplashScreen()
    }
}
