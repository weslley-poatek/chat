package br.com.weslleycampos.chat.core.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * One persona, table `personas`, added in version 3: a name and a system prompt that answer with
 * one model. Personas are the user's data and live on this device; the app seeds the design's
 * presets into this table, and nothing deletes them.
 *
 * Conversations and replies name a persona by [id] without a foreign key, so a persona that is
 * gone reads as none rather than taking its conversations with it.
 *
 * @property id Stable key, stored with conversations and replies: `reviewer`, `terse`, …
 * @property name As the picker and the model chip show it: "Code reviewer".
 * @property modelId The catalogue id of the model the persona answers with, such as `sonnet`.
 * @property prompt The persona's instructions, as the picker's row meta shows them.
 * @property position Where it sits in the picker's "personas" list, lowest first.
 */
@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val modelId: String,
    val prompt: String,
    val position: Int,
)
