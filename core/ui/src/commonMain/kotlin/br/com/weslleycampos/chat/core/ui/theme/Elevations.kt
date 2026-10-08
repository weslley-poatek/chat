package br.com.weslleycampos.chat.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

@Immutable
data class ChatElevation(
    val knob: Shadow = Shadow(
        radius = 2.dp,
        color = Black.copy(alpha = 0.3f),
        offset = DpOffset(x = 0.dp, y = 1.dp),
    ),
    val glow: Dp = 8.dp,
)

val DefaultElevation = ChatElevation()

val LocalChatElevation = staticCompositionLocalOf<ChatElevation> {
    error("No ChatElevation provided. Wrap your content in ChatTheme { ... }")
}
