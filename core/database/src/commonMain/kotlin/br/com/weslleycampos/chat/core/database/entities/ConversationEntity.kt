package br.com.weslleycampos.chat.core.database.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * One chat thread, table `conversations`, added in version 2. A conversation is only created with
 * its first message, so every row has at least one message.
 *
 * Later rows add their columns with a default value, as version 4's [pinned] and [archived] do, so
 * each stays one `@AutoMigration`.
 *
 * @property id Assigned by SQLite on insert: pass `0`.
 * @property title Derived from the first message when the conversation is created.
 * @property modelId The catalogue id of the model that answers, such as `sonnet`.
 * @property createdAt Epoch milliseconds of the first message.
 * @property updatedAt Epoch milliseconds of the latest message; "recent" and History sort on it.
 * @property personaId Version 3: the id of the [PersonaEntity] that answers, or `null` for the model
 *   on its own. A persona answers with its own model, so [modelId] is that persona's model while it
 *   is set. Rows from version 2 read `null`.
 * @property pinned Version 4: History lists it under "pinned", whatever day it was updated. Pinning
 *   does not move [updatedAt]. Rows from version 3 read `false`.
 * @property archived Version 4: hidden from History and "recent", but kept with its messages until
 *   it is deleted. The chat repository clears it when a message is sent in it. Rows from version 3
 *   read `false`.
 * @property forkedFromTitle Version 5: for a fork, the title of the conversation it was forked from,
 *   as that title was at the fork; `null` for a conversation that is not a fork. It is a copy rather
 *   than a reference, so the fork banner reads the same after that conversation is deleted. Rows
 *   from version 4 read `null`.
 * @property forkedAtMessage Version 5: for a fork, the position of the message it was forked at in
 *   that conversation's thread, counting from 1 over user messages and replies alike; `null` for a
 *   conversation that is not a fork. Rows from version 4 read `null`.
 * @property forkBannerDismissed Version 5: whether the fork banner's "×" hid the banner, for good.
 *   Always `false` for a conversation that is not a fork. Rows from version 4 read `false`.
 * @property compareOn Version 6: compare mode is on, the model picker's "compare · on". It is never
 *   on while [personaId] is set. Rows from version 5 read `false`.
 * @property compareModelId Version 6: the catalogue id of the model compared with [modelId] while
 *   [compareOn], the prototype's `compareId`; `null` while compare is off, or on before a second
 *   model is picked. While both are set, a message sent in the conversation is answered by a compare
 *   pair ([MessageEntity.pairSide]). Rows from version 5 read `null`.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val personaId: String? = null,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    val forkedFromTitle: String? = null,
    val forkedAtMessage: Int? = null,
    @ColumnInfo(defaultValue = "0") val forkBannerDismissed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val compareOn: Boolean = false,
    val compareModelId: String? = null,
)
