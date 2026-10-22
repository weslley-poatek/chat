package br.com.weslleycampos.chat.core.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * One chat thread, table `conversations`, added in version 2. A conversation is only created with
 * its first message, so every row has at least one message.
 *
 * Later rows add their columns (pinned, archived, folder, persona) with a default value, so each
 * stays one `@AutoMigration`.
 *
 * @property id Assigned by SQLite on insert: pass `0`.
 * @property title Derived from the first message when the conversation is created.
 * @property modelId The catalogue id of the model that answers, such as `sonnet`.
 * @property createdAt Epoch milliseconds of the first message.
 * @property updatedAt Epoch milliseconds of the latest message; the "recent" list sorts on it.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
)
