package br.com.weslleycampos.chat.core.database

import androidx.room3.ColumnTypeConverter
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.StoredBlock

/**
 * The column types Room cannot store on its own. Enums are stored as their `value`, never their
 * Kotlin name, so renaming a constant cannot break a row already on disk.
 */
internal class ChatTypeConverters {
    @ColumnTypeConverter
    fun blocksToJson(blocks: List<StoredBlock>): String = encodeBlocks(blocks)

    @ColumnTypeConverter
    fun jsonToBlocks(value: String): List<StoredBlock> = decodeBlocks(value)

    @ColumnTypeConverter
    fun roleToValue(role: MessageRole): String = role.value

    @ColumnTypeConverter
    fun valueToRole(value: String): MessageRole = MessageRole.entries.first { it.value == value }

    @ColumnTypeConverter
    fun statusToValue(status: MessageStatus): String = status.value

    @ColumnTypeConverter
    fun valueToStatus(value: String): MessageStatus = MessageStatus.entries.first { it.value == value }
}
