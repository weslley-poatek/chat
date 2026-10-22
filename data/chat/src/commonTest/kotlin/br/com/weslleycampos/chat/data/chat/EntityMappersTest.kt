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
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class EntityMappersTest {
    private val blocks = listOf(
        Block.Paragraph("Minimal fix:"),
        Block.Code(language = "ts", text = "useEffect(() => {});"),
        Block.NumberedList(listOf("one", "two")),
    )
    private val stored = listOf(
        StoredBlock.Paragraph("Minimal fix:"),
        StoredBlock.Code(language = "ts", text = "useEffect(() => {});"),
        StoredBlock.NumberedList(listOf("one", "two")),
    )
    private val terse: Persona = PersonaPresets.personas.single { it.id == "terse" }
    private val personas: Map<String, Persona> = mapOf(terse.id to terse)

    @Test
    fun aConversationRowReadsWithItsCatalogueModelAndInstants() {
        val row = ConversationEntity(id = 3, title = "Hello", modelId = "gpt5", createdAt = 1_000, updatedAt = 2_000)

        assertEquals(
            Conversation(
                id = 3,
                title = "Hello",
                model = ModelCatalogue["gpt5"],
                createdAt = Instant.fromEpochMilliseconds(1_000),
                updatedAt = Instant.fromEpochMilliseconds(2_000),
            ),
            row.toConversation(personas),
        )
        assertEquals(ModelCatalogue.default, row.copy(modelId = "retired").toConversation(personas).model)
    }

    @Test
    fun aConversationRowReadsWithItsPinnedAndArchivedFlags() {
        val row = ConversationEntity(id = 3, title = "Hello", modelId = "gpt5", createdAt = 1_000, updatedAt = 2_000)

        val plain = row.toConversation(personas)
        val pinned = row.copy(pinned = true).toConversation(personas)
        val archived = row.copy(archived = true).toConversation(personas)

        assertEquals(false to false, plain.pinned to plain.archived)
        assertEquals(true to false, pinned.pinned to pinned.archived)
        assertEquals(false to true, archived.pinned to archived.archived)
    }

    @Test
    fun aConversationRowReadsWithItsStoredPersonaAndAnUnknownOneAsNone() {
        val row = ConversationEntity(
            id = 3,
            title = "Hello",
            modelId = "gpt5",
            createdAt = 1_000,
            updatedAt = 2_000,
            personaId = "terse",
        )

        assertEquals(terse, row.toConversation(personas).persona)
        assertNull(row.copy(personaId = "ghost").toConversation(personas).persona)
        assertNull(row.copy(personaId = null).toConversation(personas).persona)
    }

    @Test
    fun aUserRowReadsAsItsText() {
        val row = MessageEntity(
            id = 5,
            conversationId = 3,
            role = MessageRole.USER,
            text = "Add a leading option",
            blocks = emptyList(),
            modelId = null,
            status = MessageStatus.DONE,
            latencyMillis = null,
            createdAt = 1_000,
        )

        assertEquals(
            Message.User(
                id = 5,
                conversationId = 3,
                text = "Add a leading option",
                createdAt = Instant.fromEpochMilliseconds(1_000),
            ),
            row.toMessage(personas),
        )
    }

    @Test
    fun anAssistantRowReadsWithItsBlocksStatusAndLatency() {
        val row = MessageEntity(
            id = 6,
            conversationId = 3,
            role = MessageRole.ASSISTANT,
            text = "",
            blocks = stored,
            modelId = "opus",
            status = MessageStatus.DONE,
            latencyMillis = 1_900,
            createdAt = 1_000,
        )

        assertEquals(
            Message.Assistant(
                id = 6,
                conversationId = 3,
                model = ModelCatalogue["opus"],
                blocks = blocks,
                status = ReplyStatus.DONE,
                latency = 1_900.milliseconds,
                createdAt = Instant.fromEpochMilliseconds(1_000),
            ),
            row.toMessage(personas),
        )
        assertEquals(ModelCatalogue.default, (row.copy(modelId = null).toMessage(personas) as Message.Assistant).model)
        assertEquals(terse, (row.copy(personaId = "terse").toMessage(personas) as Message.Assistant).persona)
        assertNull((row.copy(personaId = "ghost").toMessage(personas) as Message.Assistant).persona)
    }

    @Test
    fun aPersonaRowReadsWithItsCatalogueModelAndBack() {
        val row = PersonaEntity(id = "terse", name = "Terse", modelId = "gpt5", prompt = terse.prompt, position = 1)

        assertEquals(terse, row.toPersona())
        assertEquals(row, terse.toEntity(position = 1))
        assertEquals(ModelCatalogue.default, row.copy(modelId = "retired").toPersona().model)
    }

    @Test
    fun everyStoredStatusHasItsReplyStatus() {
        assertEquals(
            listOf(ReplyStatus.THINKING, ReplyStatus.STREAMING, ReplyStatus.DONE, ReplyStatus.INTERRUPTED),
            MessageStatus.entries.map { it.toReplyStatus() },
        )
        assertEquals(listOf(true, true, false, false), ReplyStatus.entries.map { it.isInProgress })
    }

    @Test
    fun blocksRoundTripBetweenDomainAndStorage() {
        assertEquals(stored, blocks.map(Block::toStoredBlock))
        assertEquals(blocks, stored.map(StoredBlock::toBlock))
    }
}
