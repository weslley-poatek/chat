package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PersonaEntity
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

// Rows to domain models and back. A model id the catalogue no longer has reads as its default model,
// and a persona id that is not among the stored personas reads as no persona.

internal fun ConversationEntity.toConversation(personas: Map<String, Persona>): Conversation = Conversation(
    id = id,
    title = title,
    model = ModelCatalogue[modelId],
    persona = personaId?.let(personas::get),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    pinned = pinned,
    archived = archived,
)

internal fun MessageEntity.toMessage(personas: Map<String, Persona>): Message = when (role) {
    MessageRole.USER -> Message.User(
        id = id,
        conversationId = conversationId,
        text = text,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )
    MessageRole.ASSISTANT -> Message.Assistant(
        id = id,
        conversationId = conversationId,
        model = modelId?.let { ModelCatalogue[it] } ?: ModelCatalogue.default,
        persona = personaId?.let(personas::get),
        blocks = blocks.map(StoredBlock::toBlock),
        status = status.toReplyStatus(),
        latency = latencyMillis?.milliseconds,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )
}

internal fun PersonaEntity.toPersona(): Persona = Persona(
    id = id,
    name = name,
    model = ModelCatalogue[modelId],
    prompt = prompt,
)

/** The row for [Persona], [position] places it in the picker's list. */
internal fun Persona.toEntity(position: Int): PersonaEntity = PersonaEntity(
    id = id,
    name = name,
    modelId = model.id,
    prompt = prompt,
    position = position,
)

internal fun MessageStatus.toReplyStatus(): ReplyStatus = when (this) {
    MessageStatus.THINKING -> ReplyStatus.THINKING
    MessageStatus.STREAMING -> ReplyStatus.STREAMING
    MessageStatus.DONE -> ReplyStatus.DONE
    MessageStatus.INTERRUPTED -> ReplyStatus.INTERRUPTED
}

internal fun StoredBlock.toBlock(): Block = when (this) {
    is StoredBlock.Paragraph -> Block.Paragraph(text = text)
    is StoredBlock.Code -> Block.Code(language = language, text = text)
    is StoredBlock.NumberedList -> Block.NumberedList(items = items)
}

internal fun Block.toStoredBlock(): StoredBlock = when (this) {
    is Block.Paragraph -> StoredBlock.Paragraph(text = text)
    is Block.Code -> StoredBlock.Code(language = language, text = text)
    is Block.NumberedList -> StoredBlock.NumberedList(items = items)
}
