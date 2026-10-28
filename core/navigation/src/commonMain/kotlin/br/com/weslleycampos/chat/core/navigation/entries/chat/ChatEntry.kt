package br.com.weslleycampos.chat.core.navigation.entries.chat

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The chat thread: a conversation read and continued as text. Voice's "Aa" pushes it over Voice and
 * History's rows push it over History; back returns to the screen underneath. Its mic opens
 * `VoiceEntry(conversationId)` in place of Voice and everything over it.
 *
 * @property conversationId The conversation to open, or `null` for a new chat: the empty state, with
 *   the conversation created by its first message.
 */
@Serializable
data class ChatEntry(val conversationId: Long? = null) : NavKey
