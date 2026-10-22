package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
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
            row.toConversation(),
        )
        assertEquals(ModelCatalogue.default, row.copy(modelId = "retired").toConversation().model)
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
            row.toMessage(),
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
            row.toMessage(),
        )
        assertEquals(ModelCatalogue.default, (row.copy(modelId = null).toMessage() as Message.Assistant).model)
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
