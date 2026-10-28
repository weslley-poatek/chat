package br.com.weslleycampos.chat.core.navigation.entries.chat

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The chat thread: a conversation read and continued as text. Voice's "Aa" pushes it over Voice,
 * and back returns there; History's rows open it too.
 *
 * @property conversationId The conversation to open, or `null` for a new chat: the empty state, with
 *   the conversation created by its first message.
 */
@Serializable
data class ChatEntry(val conversationId: Long? = null) : NavKey
