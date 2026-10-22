package br.com.weslleycampos.chat.core.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Room refuses a `@Database` without entities, and version 1 ships before the first real table.
 * This one holds no rows and nothing reads or writes it. It stays in every later version, so no
 * migration ever has to drop it.
 */
@Entity(tableName = "placeholder")
data class PlaceholderEntity(
    @PrimaryKey val id: Long,
)
