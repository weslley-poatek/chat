package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.models.CompareSide
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.Persona
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// A thread's rows to its messages, compare pairs included. One row reads as in EntityMappers.kt.

/**
 * A conversation's rows, in the order they were written, as its messages: each compare pair's two
 * rows, a first side and the second side right after it, read as one [Message.ComparePair]. A side
 * whose partner is missing reads as a reply of its own.
 */
internal fun List<MessageEntity>.toMessages(personas: Map<String, Persona>): List<Message> = buildList {
    var index = 0
    while (index < this@toMessages.size) {
        val row = this@toMessages[index]
        val partner = this@toMessages.getOrNull(index + 1)
        if (row.pairSide == PAIR_FIRST_SIDE && partner?.pairSide == PAIR_SECOND_SIDE) {
            add(comparePair(first = row, second = partner))
            index += 2
        } else {
            add(row.toMessage(personas))
            index++
        }
    }
}

private fun comparePair(first: MessageEntity, second: MessageEntity): Message.ComparePair = Message.ComparePair(
    id = first.id,
    conversationId = first.conversationId,
    sides = listOf(first.toCompareSide(), second.toCompareSide()),
    createdAt = Instant.fromEpochMilliseconds(first.createdAt),
)

private fun MessageEntity.toCompareSide(): CompareSide = CompareSide(
    id = id,
    model = modelId?.let { ModelCatalogue[it] } ?: ModelCatalogue.default,
    blocks = blocks.map(StoredBlock::toBlock),
    status = status.toReplyStatus(),
    latency = latencyMillis?.milliseconds,
)
