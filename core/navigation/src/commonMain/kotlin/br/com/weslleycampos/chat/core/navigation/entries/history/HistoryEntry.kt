package br.com.weslleycampos.chat.core.navigation.entries.history

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * History: every conversation kept on this device, grouped by day, with search and the row actions
 * pin, archive and delete. Chat's History button pushes it over the chat; its back returns to that
 * chat, and a row replaces the chat underneath with `ChatEntry(conversationId)`.
 */
@Serializable
data object HistoryEntry : NavKey
