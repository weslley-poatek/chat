package br.com.weslleycampos.chat.core.navigation.entries.history

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * History: every conversation kept on this device, grouped by day, with search and the row actions
 * pin, archive and delete. Voice's History button pushes it over Voice, and its back returns there.
 * A row pushes `ChatEntry(conversationId)` over History, so back from the thread returns here.
 */
@Serializable
data object HistoryEntry : NavKey
