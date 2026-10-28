package br.com.weslleycampos.chat.core.navigation.entries.history

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * History: every conversation kept on this device, grouped by day, with search and the row actions
 * pin, archive and delete. Voice's and Chat's History buttons push it over their screen, and its
 * back returns there. A row opens `ChatEntry(conversationId)`, in place of a chat underneath.
 */
@Serializable
data object HistoryEntry : NavKey
