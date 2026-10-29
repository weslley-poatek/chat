package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.core.database.entities.MessageEntity
import br.com.weslleycampos.chat.core.database.entities.MessageRole
import br.com.weslleycampos.chat.core.database.entities.MessageStatus
import br.com.weslleycampos.chat.core.database.entities.PAIR_FIRST_SIDE
import br.com.weslleycampos.chat.core.database.entities.PAIR_SECOND_SIDE
import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.fuelix.FuelixMessage
import br.com.weslleycampos.chat.data.fuelix.FuelixRole
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyPromptsTest {
    private val terse: Persona = PersonaPresets.personas.single { it.id == "terse" }

    private fun user(id: Long, text: String) = MessageEntity(
        id = id,
        conversationId = 1,
        role = MessageRole.USER,
        text = text,
        blocks = emptyList(),
        modelId = null,
        status = MessageStatus.DONE,
        latencyMillis = null,
        createdAt = 0,
    )

    private fun reply(
        id: Long,
        modelId: String? = GPT,
        blocks: List<StoredBlock> = emptyList(),
        pairSide: Int? = null,
        personaId: String? = null,
        status: MessageStatus = MessageStatus.DONE,
    ) = MessageEntity(
        id = id,
        conversationId = 1,
        role = MessageRole.ASSISTANT,
        text = "",
        blocks = blocks,
        modelId = modelId,
        status = status,
        latencyMillis = null,
        createdAt = 0,
        personaId = personaId,
        pairSide = pairSide,
    )

    private fun said(text: String) = listOf(StoredBlock.Paragraph(text))

    private fun asUser(text: String) = FuelixMessage(FuelixRole.USER, text)

    private fun asModel(text: String) = FuelixMessage(FuelixRole.ASSISTANT, text)

    /** "SSE or WebSocket?" answered by a pair, Sonnet's side first, then "Why?". */
    private val compared = listOf(
        user(id = 1, text = "SSE or WebSocket?"),
        reply(id = 2, modelId = SONNET, blocks = said("Use SSE."), pairSide = PAIR_FIRST_SIDE),
        reply(id = 3, modelId = GPT, blocks = said("Use WebSockets."), pairSide = PAIR_SECOND_SIDE),
        user(id = 4, text = "Why?"),
    )

    @Test
    fun aRequestSendsTheSystemPromptFirstThenTheConversationBeforeItsReply() {
        val thread = listOf(
            user(id = 1, text = "Write a useDebounce hook"),
            reply(id = 2, blocks = said("Here it is.")),
            user(id = 3, text = "Add a leading option"),
            reply(id = 4, status = MessageStatus.THINKING),
            user(id = 5, text = "Sent later"),
        )

        val request = ReplyPrompts.request(reply = thread[3], model = GPT, thread = thread, system = "Be terse.")

        assertEquals(4L to GPT, request.replyId to request.model)
        assertEquals(
            listOf(
                FuelixMessage(FuelixRole.SYSTEM, "Be terse."),
                asUser("Write a useDebounce hook"),
                asModel("Here it is."),
                asUser("Add a leading option"),
            ),
            request.messages,
        )
    }

    @Test
    fun aMissingOrBlankSystemPromptSendsNone() {
        val thread = listOf(user(id = 1, text = "hi"))

        assertEquals(listOf(asUser("hi")), ReplyPrompts.messages(thread, model = GPT, system = null))
        assertEquals(listOf(asUser("hi")), ReplyPrompts.messages(thread, model = GPT, system = " \n"))
    }

    @Test
    fun aReplyIsSentAsItsBlocksWrittenBackAsMarkdownAndOneThatShowedNothingIsLeftOut() {
        val blocks = listOf(
            StoredBlock.Paragraph("Minimal fix:"),
            StoredBlock.Code(language = "ts", text = "useEffect(() => {});"),
            StoredBlock.NumberedList(listOf("one", "two")),
        )
        val thread = listOf(
            user(id = 1, text = "Fix it"),
            reply(id = 2, blocks = blocks),
            user(id = 3, text = "Again"),
            reply(id = 4, status = MessageStatus.INTERRUPTED),
            user(id = 5, text = "Once more"),
            reply(id = 6, blocks = said(" ")),
        )

        assertEquals(
            listOf(
                asUser("Fix it"),
                asModel("Minimal fix:\n\n```ts\nuseEffect(() => {});\n```\n\n1. one\n2. two"),
                asUser("Again"),
                asUser("Once more"),
            ),
            ReplyPrompts.messages(thread, model = GPT, system = null),
        )
    }

    @Test
    fun ofAComparePairAModelReadsItsOwnSideOrTheFirstWhenNeitherIsItsOwn() {
        fun read(model: String, thread: List<MessageEntity> = compared) =
            ReplyPrompts.messages(thread, model = model, system = null)

        assertEquals(listOf(asUser("SSE or WebSocket?"), asModel("Use WebSockets."), asUser("Why?")), read(GPT))
        assertEquals(listOf(asUser("SSE or WebSocket?"), asModel("Use SSE."), asUser("Why?")), read(SONNET))
        assertEquals(listOf(asUser("SSE or WebSocket?"), asModel("Use SSE."), asUser("Why?")), read(GEMINI))
        // A side stored before Fuelix is read by the model its id became.
        val legacy = compared.map { if (it.id == 3L) it.copy(modelId = "gpt5") else it }
        assertEquals(asModel("Use WebSockets."), read(GPT, legacy)[1])
    }

    @Test
    fun eachSideOfANewPairAsksItsOwnModelAndTheSecondSideLeavesOutItsPartner() {
        val thread = compared + listOf(
            reply(id = 5, modelId = SONNET, blocks = said("Already streaming"), pairSide = PAIR_FIRST_SIDE),
            reply(id = 6, modelId = GPT, pairSide = PAIR_SECOND_SIDE, status = MessageStatus.THINKING),
        )

        val (first, second) = ReplyPrompts.requests(replyIds = listOf(5, 6), thread = thread, personas = emptyMap())

        assertEquals(5L to SONNET, first.replyId to first.model)
        assertEquals(listOf(asUser("SSE or WebSocket?"), asModel("Use SSE."), asUser("Why?")), first.messages)
        assertEquals(6L to GPT, second.replyId to second.model)
        assertEquals(listOf(asUser("SSE or WebSocket?"), asModel("Use WebSockets."), asUser("Why?")), second.messages)
    }

    @Test
    fun aReplyIsAskedOfTheModelItsRowWasSavedWithAndItsPersonasPromptIsTheSystemPrompt() {
        val thread = listOf(
            user(id = 1, text = "Review this diff"),
            reply(id = 2, modelId = "gpt5", personaId = terse.id, status = MessageStatus.THINKING),
            reply(id = 3, modelId = null, personaId = "ghost", status = MessageStatus.THINKING),
        )

        val requests = ReplyPrompts.requests(
            replyIds = listOf(2, 3, 42),
            thread = thread,
            personas = mapOf(terse.id to terse),
        )

        assertEquals(listOf(2L, 3L), requests.map { it.replyId })
        assertEquals(listOf(GPT, ModelCatalogue.DEFAULT_MODEL_ID), requests.map { it.model })
        assertEquals(
            listOf(FuelixMessage(FuelixRole.SYSTEM, terse.prompt), asUser("Review this diff")),
            requests[0].messages,
        )
        assertEquals(listOf(asUser("Review this diff")), requests[1].messages)
    }

    private companion object {
        const val SONNET = "claude-sonnet-4-5-20250929"
        const val GPT = "gpt-5"
        const val GEMINI = "gemini-2.5-pro"
    }
}
