package br.com.weslleycampos.chat.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ChatSizes(
    val xxxSmall: Dp = 4.dp,
    val xxSmall: Dp = 14.dp,
    val xSmall: Dp = 20.dp,
    val small: Dp = 22.dp,
    val medium: Dp = 26.dp,
    val large: Dp = 30.dp,
    val xLarge: Dp = 36.dp,
    val xxLarge: Dp = 44.dp,
    val xxxLarge: Dp = 48.dp,
)

val DefaultSizes = ChatSizes()

val LocalChatSizes = staticCompositionLocalOf<ChatSizes> {
    error("No ChatSizes provided. Wrap your content in ChatTheme { ... }")
}
