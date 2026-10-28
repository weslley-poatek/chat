package br.com.weslleycampos.chat.core.navigation.entries.voice

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Voice mode, the home screen: the session gate and sign-in replace themselves with `VoiceEntry()`,
 * the root of the back stack, so back from it leaves the app. Its History button pushes
 * `HistoryEntry` and its "Aa" pushes `ChatEntry(conversationId)`; back from either returns here.
 *
 * @property conversationId The conversation to go on with in voice, or `null` for a new session,
 *   whose first exchange creates its conversation.
 */
@Serializable
data class VoiceEntry(val conversationId: Long? = null) : NavKey
