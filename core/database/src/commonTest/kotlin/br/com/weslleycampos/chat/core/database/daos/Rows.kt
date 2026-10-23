package br.com.weslleycampos.chat.core.database.daos

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.core.database.entities.StoredBlock

internal fun conversation(
    title: String = "Hello",
    updatedAt: Long = 0,
    modelId: String = "sonnet",
    pinned: Boolean = false,
    archived: Boolean = false,
    forkedFromTitle: String? = null,
    forkedAtMessage: Int? = null,
    compareOn: Boolean = false,
    compareModelId: String? = null,
) = ConversationEntity(
    title = title,
    modelId = modelId,
    createdAt = updatedAt,
    updatedAt = updatedAt,
    pinned = pinned,
    archived = archived,
    forkedFromTitle = forkedFromTitle,
    forkedAtMessage = forkedAtMessage,
    compareOn = compareOn,
    compareModelId = compareModelId,
)

internal fun userMessage(conversationId: Long, text: String = "hi", createdAt: Long = 0) = MessageEntity(
    conversationId = conversationId,
    role = MessageRole.USER,
    text = text,
    blocks = emptyList(),
    modelId = null,
    status = MessageStatus.DONE,
    latencyMillis = null,
    createdAt = createdAt,
)

internal fun reply(
    conversationId: Long,
    status: MessageStatus = MessageStatus.DONE,
    blocks: List<StoredBlock> = listOf(StoredBlock.Paragraph("ok")),
    createdAt: Long = 0,
) = MessageEntity(
    conversationId = conversationId,
    role = MessageRole.ASSISTANT,
    text = "",
    blocks = blocks,
    modelId = "sonnet",
    status = status,
    latencyMillis = if (status == MessageStatus.DONE) 800 else null,
    createdAt = createdAt,
)

/** One side of a compare pair: [PAIR_FIRST_SIDE] on sonnet, or [PAIR_SECOND_SIDE] on [modelId]. */
internal fun pairSide(
    conversationId: Long,
    side: Int,
    modelId: String = if (side == PAIR_FIRST_SIDE) "sonnet" else "gpt5",
    status: MessageStatus = MessageStatus.DONE,
    blocks: List<StoredBlock> = listOf(StoredBlock.Paragraph("ok")),
) = reply(conversationId, status = status, blocks = blocks).copy(modelId = modelId, pairSide = side)
