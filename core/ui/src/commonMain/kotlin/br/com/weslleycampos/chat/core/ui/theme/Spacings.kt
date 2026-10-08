package br.com.weslleycampos.chat.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ChatSpacing(
    val xxxSmall: Dp = 2.dp,
    val xxSmall: Dp = 4.dp,
    val xSmall: Dp = 6.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 10.dp,
    val large: Dp = 12.dp,
    val xLarge: Dp = 14.dp,
    val xxLarge: Dp = 16.dp,
    val xxxLarge: Dp = 22.dp,
)

val DefaultSpacing = ChatSpacing()

val LocalChatSpacing = staticCompositionLocalOf<ChatSpacing> {
    error("No ChatSpacing provided. Wrap your content in ChatTheme { ... }")
}
