package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.CompareSide
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class ThreadMappersTest {
    private fun row(
        id: Long,
        role: MessageRole = MessageRole.ASSISTANT,
        modelId: String? = "sonnet",
        pairSide: Int? = null,
        status: MessageStatus = MessageStatus.DONE,
        latencyMillis: Long? = 900,
        createdAt: Long = 1_000,
    ) = MessageEntity(
        id = id,
        conversationId = 4,
        role = role,
        text = if (role == MessageRole.USER) "SSE or WebSocket?" else "",
        blocks = if (role == MessageRole.USER) emptyList() else listOf(StoredBlock.Paragraph("Use SSE")),
        modelId = if (role == MessageRole.USER) null else modelId,
        status = status,
        latencyMillis = latencyMillis,
        createdAt = createdAt,
        pairSide = pairSide,
    )

    @Test
    fun aPairsTwoRowsReadAsOneComparePairOnItsFirstSidesIdAndTime() {
        val rows = listOf(
            row(id = 1, role = MessageRole.USER, createdAt = 500),
            row(id = 2, pairSide = PAIR_FIRST_SIDE, latencyMillis = 5_208, createdAt = 500),
            row(id = 3, modelId = "gpt5", pairSide = PAIR_SECOND_SIDE, latencyMillis = 2_212, createdAt = 600),
        )

        val messages = rows.toMessages(personas = emptyMap())

        assertEquals(2, messages.size)
        assertIs<Message.User>(messages[0])
        assertEquals(
            Message.ComparePair(
                id = 2,
                conversationId = 4,
                sides = listOf(
                    CompareSide(
                        id = 2,
                        model = ModelCatalogue["sonnet"],
                        blocks = listOf(Block.Paragraph("Use SSE")),
                        status = ReplyStatus.DONE,
                        latency = 5_208.milliseconds,
                    ),
                    CompareSide(
                        id = 3,
                        model = ModelCatalogue["gpt5"],
                        blocks = listOf(Block.Paragraph("Use SSE")),
                        status = ReplyStatus.DONE,
                        latency = 2_212.milliseconds,
                    ),
                ),
                createdAt = Instant.fromEpochMilliseconds(500),
            ),
            messages[1],
        )
    }

    @Test
    fun aPairIsInProgressWhileEitherSideThinksOrStreams() {
        fun pair(first: MessageStatus, second: MessageStatus) = listOf(
            row(id = 2, pairSide = PAIR_FIRST_SIDE, status = first),
            row(id = 3, modelId = "gpt5", pairSide = PAIR_SECOND_SIDE, status = second),
        ).toMessages(personas = emptyMap()).single() as Message.ComparePair

        assertTrue(pair(MessageStatus.THINKING, MessageStatus.THINKING).isInProgress)
        assertTrue(pair(MessageStatus.STREAMING, MessageStatus.DONE).isInProgress)
        assertTrue(pair(MessageStatus.DONE, MessageStatus.STREAMING).isInProgress)
        assertFalse(pair(MessageStatus.DONE, MessageStatus.INTERRUPTED).isInProgress)
        assertFalse(pair(MessageStatus.DONE, MessageStatus.DONE).isInProgress)
    }

    @Test
    fun singleRepliesAndPairsReadInTheOrderTheyWereWritten() {
        val rows = listOf(
            row(id = 1, role = MessageRole.USER),
            row(id = 2),
            row(id = 3, role = MessageRole.USER),
            row(id = 4, pairSide = PAIR_FIRST_SIDE),
            row(id = 5, modelId = "gpt5", pairSide = PAIR_SECOND_SIDE),
            row(id = 6, role = MessageRole.USER),
            row(id = 7, modelId = "gemini"),
        )

        val messages = rows.toMessages(personas = emptyMap())

        assertEquals(listOf(1L, 2, 3, 4, 6, 7), messages.map { it.id })
        assertEquals(
            listOf("User", "Assistant", "User", "ComparePair", "User", "Assistant"),
            messages.map { it::class.simpleName },
        )
    }

    @Test
    fun aSideWithoutItsPartnerReadsAsAReplyOfItsOwn() {
        val rows = listOf(
            row(id = 1, role = MessageRole.USER),
            row(id = 2, pairSide = PAIR_FIRST_SIDE),
            row(id = 3, role = MessageRole.USER),
            row(id = 4, modelId = "gpt5", pairSide = PAIR_SECOND_SIDE),
        )

        val messages = rows.toMessages(personas = emptyMap())

        assertEquals(listOf(1L, 2, 3, 4), messages.map { it.id })
        assertEquals(ModelCatalogue["sonnet"], assertIs<Message.Assistant>(messages[1]).model)
        assertEquals(ModelCatalogue["gpt5"], assertIs<Message.Assistant>(messages[3]).model)
    }
}
