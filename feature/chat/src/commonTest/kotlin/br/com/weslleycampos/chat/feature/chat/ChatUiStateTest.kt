package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.ForkOrigin
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerVariant
import br.com.weslleycampos.chat.feature.chat.picker.modelPickerUiState
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_forked
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_send_failed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

private const val ReplyId = 2L

class ChatUiStateTest {
    private fun reply(status: ReplyStatus, blocks: List<Block> = ReplyBlocks) = Message.Assistant(
        id = ReplyId,
        conversationId = 1,
        model = ModelCatalogue.default,
        blocks = blocks,
        status = status,
        latency = 812.milliseconds.takeIf { status == ReplyStatus.DONE },
        createdAt = Now,
    ).toUi(testTimes())

    private fun key(index: Int) = BlockKey(messageId = ReplyId, index = index)

    private val MessageUi.Assistant.carets: List<Boolean>
        get() = blocks.map { block ->
            when (block) {
                is BlockUi.Paragraph -> block.caret
                is BlockUi.Code -> block.caret
                is BlockUi.NumberedList -> false
            }
        }

    @Test
    fun aUserMessageShowsItsTextAtItsLocalTime() {
        val message = Message.User(id = 1, conversationId = 1, text = "Why?", createdAt = Now).toUi(testTimes())

        assertEquals(MessageUi.User(id = 1, time = "14:02", text = "Why?"), message)
    }

    @Test
    fun aDoneReplyShowsEveryBlockAndItsLatencyWithoutACaret() {
        val message = assertIs<MessageUi.Assistant>(reply(ReplyStatus.DONE))

        assertEquals(
            MessageUi.Assistant(
                id = ReplyId,
                time = "14:02",
                modelShortName = "sonnet-4.5",
                personaName = null,
                latency = "0.8s",
                interrupted = false,
                thinking = false,
                blocks = listOf(
                    BlockUi.Paragraph(key(0), "Two likely causes, in order of probability:", caret = false),
                    BlockUi.NumberedList(
                        key(1),
                        listOf(NumberedItem("1.", "A stale value."), NumberedItem("2.", "A reassigned timeout id.")),
                    ),
                    BlockUi.Paragraph(key(2), "Minimal fix:", caret = false),
                    BlockUi.Code(
                        key(ReplyCodeIndex),
                        language = "ts",
                        text = "return () => clearTimeout(id);",
                        tokens = tokenize("return () => clearTimeout(id);"),
                        caret = false,
                    ),
                    BlockUi.Paragraph(key(4), "If it still reads stale, log value inside the callback.", caret = false),
                ),
            ),
            message,
        )
    }

    @Test
    fun aReplyCarriesThePersonaItWasWrittenAsOrNone() {
        fun reply(persona: Persona?) = Message.Assistant(
            id = ReplyId,
            conversationId = 1,
            model = persona?.model ?: Gemini,
            persona = persona,
            blocks = ReplyBlocks,
            status = ReplyStatus.DONE,
            latency = 812.milliseconds,
            createdAt = Now,
        ).toUi(testTimes())

        val asReviewer = assertIs<MessageUi.Assistant>(reply(Reviewer))
        val alone = assertIs<MessageUi.Assistant>(reply(null))

        assertEquals("sonnet-4.5" to "Code reviewer", asReviewer.modelShortName to asReviewer.personaName)
        assertEquals("gemini-2.5-pro" to null, alone.modelShortName to alone.personaName)
    }

    @Test
    fun theChipThePlaceholderAndThePickerFollowTheShownChoice() {
        val onModel = ChatUiState(choice = ModelChoice(Gemini), favorites = listOf(Gemini), personas = listOf(Reviewer))
        val onPersona = onModel.copy(choice = ModelChoice(Reviewer))

        assertEquals("Gemini 2.5 Pro", onModel.chipLabel)
        assertEquals(Gemini, onModel.model)
        assertNull(onModel.persona)
        assertEquals("Code reviewer · sonnet-4.5", onPersona.chipLabel)
        assertEquals(ModelCatalogue.default, onPersona.model)
        assertEquals(Reviewer, onPersona.persona)
        assertEquals(ModelPickerVariant.Chat, onPersona.modelPicker.variant)
        assertEquals(
            modelPickerUiState(ModelPickerVariant.Chat, listOf(Gemini), listOf(Reviewer), ModelChoice(Reviewer)),
            onPersona.modelPicker,
        )
        assertFalse(ChatUiState().modelPickerOpen)
    }

    @Test
    fun onlyTheLastBlockOfAStreamingReplyHasTheCaret() {
        val paragraphLast = assertIs<MessageUi.Assistant>(reply(ReplyStatus.STREAMING))
        val codeLast = assertIs<MessageUi.Assistant>(reply(ReplyStatus.STREAMING, ReplyBlocks.take(ReplyCodeIndex + 1)))

        assertEquals(listOf(false, false, false, false, true), paragraphLast.carets)
        assertEquals(listOf(false, false, false, true), codeLast.carets)
        assertNull(paragraphLast.latency)
        assertFalse(paragraphLast.thinking)
    }

    @Test
    fun aListHasNoCaretEvenWhileItStreamsLast() {
        val message = assertIs<MessageUi.Assistant>(reply(ReplyStatus.STREAMING, ReplyBlocks.take(2)))

        assertEquals(listOf(false, false), message.carets)
        assertEquals(listOf("1.", "2."), assertIs<BlockUi.NumberedList>(message.blocks.last()).items.map { it.number })
    }

    @Test
    fun aReplyInProgressWithNoBlockYetIsThinking() {
        assertTrue(assertIs<MessageUi.Assistant>(reply(ReplyStatus.THINKING, emptyList())).thinking)
        assertTrue(assertIs<MessageUi.Assistant>(reply(ReplyStatus.STREAMING, emptyList())).thinking)
        assertFalse(assertIs<MessageUi.Assistant>(reply(ReplyStatus.INTERRUPTED, emptyList())).thinking)
        assertFalse(assertIs<MessageUi.Assistant>(reply(ReplyStatus.DONE, emptyList())).thinking)
    }

    @Test
    fun anInterruptedReplyKeepsWhatItRevealedWithoutCaretOrLatency() {
        val message = assertIs<MessageUi.Assistant>(reply(ReplyStatus.INTERRUPTED, ReplyBlocks.take(3)))

        assertTrue(message.interrupted)
        assertEquals(3, message.blocks.size)
        assertEquals(listOf(false, false, false), message.carets)
        assertNull(message.latency)
    }

    @Test
    fun onlyAMessageThatIsNotOnItsWayIsSelectable() {
        val user = Message.User(id = 1, conversationId = 1, text = "Why?", createdAt = Now).toUi(testTimes())

        assertTrue(user.selectable)
        assertEquals(
            listOf(true, true, false, false),
            ReplyStatus.entries.map { assertIs<MessageUi.Assistant>(reply(it)).inProgress },
        )
        assertEquals(listOf(false, false, true, true), ReplyStatus.entries.map { reply(it).selectable })
    }

    @Test
    fun theForkBannerShowsOnlyWithAForkOrigin() {
        val forked = ChatUiState(
            conversationId = 1,
            forkOrigin = ForkOrigin(sourceTitle = "useDebounce hook", messageNumber = 2),
        )

        assertNull(ChatUiState(conversationId = 1).forkBanner)
        assertEquals("forked from “useDebounce hook” at message 2", forked.forkBanner)
    }

    @Test
    fun aReplyIsInProgressOnlyWhileTheLastMessageIsThinkingOrStreaming() {
        val user = Message.User(id = 1, conversationId = 1, text = "Why?", createdAt = Now)
        fun assistant(status: ReplyStatus) = Message.Assistant(
            id = ReplyId,
            conversationId = 1,
            model = ModelCatalogue.default,
            blocks = emptyList(),
            status = status,
            latency = null,
            createdAt = Now,
        )

        assertFalse(emptyList<Message>().replyInProgress())
        assertFalse(listOf(user).replyInProgress())
        assertEquals(
            listOf(true, true, false, false),
            ReplyStatus.entries.map { listOf(user, assistant(it)).replyInProgress() },
        )
    }

    @Test
    fun sendNeedsTextAndNoReplyOrSendOnItsWay() {
        assertFalse(ChatUiState().canSend)
        assertFalse(ChatUiState(draft = " \n ").canSend)
        assertTrue(ChatUiState(draft = "Why?").canSend)
        assertFalse(ChatUiState(draft = "Why?", replyInProgress = true).canSend)
        assertFalse(ChatUiState(draft = "Why?", sending = true).canSend)
    }

    @Test
    fun onlyANewChatShowsTheEmptyState() {
        assertTrue(ChatUiState().showsEmptyState)
        assertFalse(ChatUiState(conversationId = 1).showsEmptyState)
    }

    @Test
    fun theComposerIsAsTallAsTheDraftsLines() {
        assertEquals(1, ChatUiState().draftRows)
        assertEquals(3, ChatUiState(draft = "a\nb\nc").draftRows)
    }

    @Test
    fun aCodeBlockIsFoundByItsKey() {
        val messages = listOf(reply(ReplyStatus.DONE))

        assertEquals("return () => clearTimeout(id);", messages.codeBlock(key(ReplyCodeIndex))?.text)
        assertNull(messages.codeBlock(key(0)))
        assertNull(messages.codeBlock(BlockKey(messageId = 9, index = ReplyCodeIndex)))
    }

    @Test
    fun aConversationIsARecentRowWithItsTitleAndLastUpdate() {
        val conversation = Conversation(
            id = 7,
            title = "Postgres index on partial JSONB column",
            model = Gemini,
            createdAt = Now - 30.hours,
            updatedAt = Now - 3.hours,
        )

        assertEquals(
            RecentConversationUi(id = 7, title = "Postgres index on partial JSONB column", time = "11:02"),
            conversation.toRecentUi(testTimes()),
        )
    }

    @Test
    fun previewsCoverEveryStateInLightAndDark() {
        val cases = ChatScreenPreviewParameterProvider().values.toList()
        val names = listOf(
            "Empty",
            "Empty · recent",
            "Empty · persona",
            "Thinking",
            "Streaming",
            "Done",
            "Done · persona",
            "Interrupted",
            "Long code block",
            "List",
            "Composer · multi-line draft",
            "Send disabled",
            "Toast",
            "Copied · Gemini",
            "Selected · question",
            "Selected · reply",
            "Copied · reply",
            "Reading · playing",
            "Reading · paused",
            "Fork banner",
            "Toast · forked",
        )

        assertEquals(names + names, cases.map { it.name })
        assertEquals(List(names.size) { false } + List(names.size) { true }, cases.map { it.darkTheme })
        val states = cases.take(names.size).associate { it.name to it.uiState }
        assertTrue(states.getValue("Empty").showsEmptyState)
        assertTrue(states.getValue("Empty · recent").recent.isNotEmpty())
        val emptyPersona = states.getValue("Empty · persona")
        assertTrue(emptyPersona.showsEmptyState)
        assertEquals("Code reviewer · sonnet-4.5", emptyPersona.chipLabel)
        val donePersona = states.getValue("Done · persona")
        assertEquals("Code reviewer · sonnet-4.5", donePersona.chipLabel)
        assertEquals(
            "Code reviewer",
            donePersona.messages.filterIsInstance<MessageUi.Assistant>().single().personaName,
        )
        assertTrue(states.getValue("Thinking").messages.filterIsInstance<MessageUi.Assistant>().single().thinking)
        assertTrue(states.getValue("Streaming").replyInProgress)
        assertEquals("0.8s", states.getValue("Done").messages.filterIsInstance<MessageUi.Assistant>().single().latency)
        assertTrue(states.getValue("Interrupted").messages.filterIsInstance<MessageUi.Assistant>().single().interrupted)
        assertTrue(states.getValue("Long code block").replyInProgress)
        assertTrue(states.getValue("Composer · multi-line draft").draftRows > 1)
        assertFalse(states.getValue("Send disabled").canSend)
        assertTrue(states.getValue("Send disabled").draft.isNotEmpty())
        assertEquals(FeatureChatRes.string.chat_toast_send_failed, states.getValue("Toast").toast)
        val copied = states.getValue("Copied · Gemini")
        assertEquals(Gemini, copied.model)
        val copiedBlock = assertNotNull(copied.copiedBlock)
        assertEquals(copiedBlock, copied.messages.codeBlock(copiedBlock)?.key)
        val question = states.getValue("Selected · question")
        assertIs<MessageUi.User>(question.messages.single { it.id == question.selectedMessage })
        val reply = states.getValue("Selected · reply")
        assertIs<MessageUi.Assistant>(reply.messages.single { it.id == reply.selectedMessage })
        val copiedReply = states.getValue("Copied · reply")
        assertEquals(copiedReply.selectedMessage, assertNotNull(copiedReply.copiedMessage))
        val playing = assertNotNull(states.getValue("Reading · playing").readAloud)
        val paused = assertNotNull(states.getValue("Reading · paused").readAloud)
        assertEquals(true to false, playing.playing to paused.playing)
        assertTrue(playing.progress in 0.1f..0.9f && paused.progress in 0.1f..0.9f)
        assertIs<MessageUi.Assistant>(reply.messages.single { it.id == playing.messageId })
        assertNotNull(states.getValue("Fork banner").forkBanner)
        val forked = states.getValue("Toast · forked")
        assertNotNull(forked.forkBanner)
        assertEquals(FeatureChatRes.string.chat_toast_forked, forked.toast)
    }
}
