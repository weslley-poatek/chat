package br.com.weslleycampos.chat.core.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
data class ChatShapes(
    val marker: CornerBasedShape = RoundedCornerShape(1.dp),
    val tag: CornerBasedShape = RoundedCornerShape(3.dp),
    val compact: CornerBasedShape = RoundedCornerShape(4.dp),
    val bubble: CornerBasedShape = RoundedCornerShape(5.dp),
    val control: CornerBasedShape = RoundedCornerShape(6.dp),
    val iconButton: CornerBasedShape = RoundedCornerShape(8.dp),
    val sheet: CornerBasedShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
    val pill: CornerBasedShape = RoundedCornerShape(percent = 50),
)

val DefaultShapes = ChatShapes()

val LocalChatShapes = staticCompositionLocalOf<ChatShapes> {
    error("No ChatShapes provided. Wrap your content in ChatTheme { ... }")
}
