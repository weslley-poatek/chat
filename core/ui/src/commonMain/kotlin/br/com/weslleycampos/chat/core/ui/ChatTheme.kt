package br.com.weslleycampos.chat.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import br.com.weslleycampos.chat.core.ui.theme.ChatColors
import br.com.weslleycampos.chat.core.ui.theme.ChatElevation
import br.com.weslleycampos.chat.core.ui.theme.ChatGradients
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.core.ui.theme.ChatShapes
import br.com.weslleycampos.chat.core.ui.theme.ChatSizes
import br.com.weslleycampos.chat.core.ui.theme.ChatSpacing
import br.com.weslleycampos.chat.core.ui.theme.ChatTypography
import br.com.weslleycampos.chat.core.ui.theme.DefaultElevation
import br.com.weslleycampos.chat.core.ui.theme.DefaultShapes
import br.com.weslleycampos.chat.core.ui.theme.DefaultSizes
import br.com.weslleycampos.chat.core.ui.theme.DefaultSpacing
import br.com.weslleycampos.chat.core.ui.theme.LocalChatColors
import br.com.weslleycampos.chat.core.ui.theme.LocalChatElevation
import br.com.weslleycampos.chat.core.ui.theme.LocalChatGradients
import br.com.weslleycampos.chat.core.ui.theme.LocalChatPalette
import br.com.weslleycampos.chat.core.ui.theme.LocalChatShapes
import br.com.weslleycampos.chat.core.ui.theme.LocalChatSizes
import br.com.weslleycampos.chat.core.ui.theme.LocalChatSpacing
import br.com.weslleycampos.chat.core.ui.theme.LocalChatTypography
import br.com.weslleycampos.chat.core.ui.theme.chatTypography
import br.com.weslleycampos.chat.core.ui.theme.debugColorScheme
import br.com.weslleycampos.chat.core.ui.theme.gradients

@Composable
fun ChatTheme(
    palette: ChatPalette = ChatPalette.Default,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalChatPalette provides palette,
        LocalChatColors provides palette.colors(darkTheme),
        LocalChatGradients provides palette.gradients(darkTheme),
        LocalChatElevation provides DefaultElevation,
        LocalChatTypography provides chatTypography,
        LocalChatShapes provides DefaultShapes,
        LocalChatSpacing provides DefaultSpacing,
        LocalChatSizes provides DefaultSizes,
    ) {
        MaterialTheme(
            colorScheme = debugColorScheme,
            content = content,
        )
    }
}

object ChatTheme {
    val colors: ChatColors
        @Composable @ReadOnlyComposable
        get() = LocalChatColors.current

    val gradients: ChatGradients
        @Composable @ReadOnlyComposable
        get() = LocalChatGradients.current

    val typography: ChatTypography
        @Composable @ReadOnlyComposable
        get() = LocalChatTypography.current

    val sizes: ChatSizes
        @Composable @ReadOnlyComposable
        get() = LocalChatSizes.current

    val shapes: ChatShapes
        @Composable @ReadOnlyComposable
        get() = LocalChatShapes.current

    val spacing: ChatSpacing
        @Composable @ReadOnlyComposable
        get() = LocalChatSpacing.current

    val elevation: ChatElevation
        @Composable @ReadOnlyComposable
        get() = LocalChatElevation.current
}
