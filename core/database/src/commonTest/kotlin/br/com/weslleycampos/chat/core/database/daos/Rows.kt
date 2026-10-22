package br.com.weslleycampos.chat.core.database.daos

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.StoredBlock

internal fun conversation(title: String = "Hello", updatedAt: Long = 0) = ConversationEntity(
    title = title,
    modelId = "sonnet",
    createdAt = updatedAt,
    updatedAt = updatedAt,
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
