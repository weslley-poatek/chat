package br.com.weslleycampos.chat.feature.chat

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_send_failed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

private const val Question = "My debounced value is one render behind. Why?"

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.chatViewModel(
        repository: FakeChatRepository = FakeChatRepository(backgroundScope),
        entry: ChatEntry = ChatEntry(),
        models: FakeModelRepository = FakeModelRepository(),
    ): ChatViewModel {
        val viewModel = ChatViewModel(
            entry = entry,
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(entry) }),
            repository = repository,
            models = models,
            times = testTimes(),
        )
        runCurrent()
        return viewModel
    }

    private val ChatViewModel.state: ChatUiState get() = uiState.value

    private val ChatViewModel.reply: MessageUi.Assistant get() = assertIs(state.messages.last())

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    private fun ChatViewModel.send(text: String) {
        onDraftChange(text)
        onSend()
    }

    // New chat and sending

    @Test
    fun aNewChatShowsTheEmptyStateOnTheDefaultModelWithTheThreeLatestConversations() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        repository.seed("Rewrite CI matrix", updatedAt = Now - 2.hours)
        repository.seed("Postgres index", model = Gemini, updatedAt = Now - 1.hours)
        repository.seed("useDebounce hook", updatedAt = Now)
        repository.seed("Terraform module", updatedAt = Now - 30.hours)

        val state = chatViewModel(repository).state

        assertTrue(state.showsEmptyState)
        assertEquals(ModelCatalogue.default, state.model)
        assertEquals(
            listOf("useDebounce hook" to "14:02", "Postgres index" to "13:02", "Rewrite CI matrix" to "12:02"),
            state.recent.map { it.title to it.time },
        )
        assertFalse(state.canSend)
    }

    @Test
    fun aSendFromANewChatCreatesTheConversationAndFollowsItsReplyFromThinkingToDone() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository)

        viewModel.send(Question)
        // The composer empties at once, before the database answers.
        assertEquals("", viewModel.state.draft)
        assertFalse(viewModel.state.canSend)

        runCurrent()
        assertEquals(listOf<Pair<Long?, String>>(null to Question), repository.sends)
        assertEquals<Long?>(1L, viewModel.state.conversationId)
        assertFalse(viewModel.state.showsEmptyState)
        assertEquals(Question, assertIs<MessageUi.User>(viewModel.state.messages.first()).text)
        assertTrue(viewModel.reply.thinking)
        assertTrue(viewModel.state.replyInProgress)

        advance(ThinkingTime)
        assertFalse(viewModel.reply.thinking)
        assertEquals(listOf(true), viewModel.reply.blocks.map { (it as BlockUi.Paragraph).caret })
        assertNull(viewModel.reply.latency)
        assertTrue(viewModel.state.replyInProgress)

        // The reply plays in the background, which advanceUntilIdle leaves alone.
        advance(ReplyTime - ThinkingTime)
        assertEquals(ReplyBlocks.size, viewModel.reply.blocks.size)
        assertEquals("0.8s", viewModel.reply.latency)
        assertFalse(viewModel.state.replyInProgress)
        assertFalse(viewModel.state.sending)
    }

    @Test
    fun sendIsDisabledWhileAReplyIsOnItsWay() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository)
        viewModel.send(Question)
        runCurrent()

        viewModel.send("Add a leading option")
        assertFalse(viewModel.state.canSend)
        assertEquals("Add a leading option", viewModel.state.draft)
        advance(ThinkingTime)
        viewModel.onSend()
        runCurrent()
        assertEquals(1, repository.sends.size)

        advance(ReplyTime - ThinkingTime)
        assertTrue(viewModel.state.canSend)
        viewModel.onSend()
        runCurrent()
        assertEquals(1L to "Add a leading option", repository.sends.last())
    }

    @Test
    fun aBlankDraftCannotBeSent() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository)

        viewModel.send(" \n ")
        runCurrent()

        assertFalse(viewModel.state.canSend)
        assertTrue(repository.sends.isEmpty())
    }

    @Test
    fun whenNothingWasSentTheDraftComesBackAndTheToastShowsFor1400Ms() = runTest {
        val repository = FakeChatRepository(backgroundScope).apply { answer = { _, _ -> null } }
        val viewModel = chatViewModel(repository)

        viewModel.send(Question)
        runCurrent()

        assertEquals(Question, viewModel.state.draft)
        assertEquals(FeatureChatRes.string.chat_toast_send_failed, viewModel.state.toast)
        assertTrue(viewModel.state.showsEmptyState)
        assertTrue(viewModel.state.canSend)
        advance(1399.milliseconds)
        assertEquals(FeatureChatRes.string.chat_toast_send_failed, viewModel.state.toast)
        advance(1.milliseconds)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun aSendThatFailsIsTreatedAsNothingSent() = runTest {
        val repository = FakeChatRepository(backgroundScope).apply { answer = { _, _ -> error("disk full") } }
        val viewModel = chatViewModel(repository)

        viewModel.send(Question)
        runCurrent()

        assertEquals(Question, viewModel.state.draft)
        assertEquals(FeatureChatRes.string.chat_toast_send_failed, viewModel.state.toast)
        assertFalse(viewModel.state.sending)
    }

    @Test
    fun aDraftTypedWhileTheSendWasOnItsWayIsKeptWhenNothingWasSent() = runTest {
        val answer = CompletableDeferred<Long?>()
        val repository = FakeChatRepository(backgroundScope).apply { this.answer = { _, _ -> answer.await() } }
        val viewModel = chatViewModel(repository)
        viewModel.send(Question)
        runCurrent()

        viewModel.onDraftChange("Something else")
        answer.complete(null)
        runCurrent()

        assertEquals("Something else", viewModel.state.draft)
        assertEquals(FeatureChatRes.string.chat_toast_send_failed, viewModel.state.toast)
    }

    @Test
    fun aNewToastReplacesTheShownOneAndRestartsItsTime() = runTest {
        val repository = FakeChatRepository(backgroundScope).apply { answer = { _, _ -> null } }
        val viewModel = chatViewModel(repository)
        viewModel.send(Question)
        runCurrent()
        advance(1000.milliseconds)

        viewModel.onSend()
        runCurrent()
        advance(1000.milliseconds)

        assertEquals(FeatureChatRes.string.chat_toast_send_failed, viewModel.state.toast)
        advance(400.milliseconds)
        assertNull(viewModel.state.toast)
    }

    // New chat and opening a conversation

    @Test
    fun newChatGoesBackToTheEmptyStateOnTheDefaultModelAndKeepsTheDraft() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Postgres index", model = Gemini)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        viewModel.onCopyCode(BlockKey(viewModel.reply.id, ReplyCodeIndex)) {}
        viewModel.onDraftChange("half a thought")

        viewModel.onNewChat()
        runCurrent()

        assertEquals(
            ChatUiState(
                choice = ModelChoice(ModelCatalogue.default),
                recent = viewModel.state.recent,
                draft = "half a thought",
                favorites = viewModel.state.favorites,
                personas = viewModel.state.personas,
            ),
            viewModel.state,
        )
    }

    @Test
    fun aSendThatAnswersAfterNewChatLeavesTheNewChatEmpty() = runTest {
        val answer = CompletableDeferred<Long?>()
        val repository = FakeChatRepository(backgroundScope).apply { this.answer = { _, _ -> answer.await() } }
        val viewModel = chatViewModel(repository)
        viewModel.send(Question)
        runCurrent()

        viewModel.onNewChat()
        answer.complete(repository.seed(Question).id)
        runCurrent()

        assertNull(viewModel.state.conversationId)
        assertTrue(viewModel.state.messages.isEmpty())
        assertNull(viewModel.state.toast)
    }

    @Test
    fun aRecentRowOpensThatConversationOnItsModel() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        repository.seed("useDebounce hook")
        val conversation = repository.seed("Postgres index", model = Gemini)
        val viewModel = chatViewModel(repository)

        viewModel.onOpenRecent(conversation.id)
        // The accent changes with the tap, before the thread loads.
        assertEquals(Gemini, viewModel.state.model)
        assertEquals<Long?>(conversation.id, viewModel.state.conversationId)

        runCurrent()
        assertEquals(
            listOf("Postgres index"),
            viewModel.state.messages.filterIsInstance<MessageUi.User>().map { it.text },
        )
        assertEquals("gemini-2.5-pro", viewModel.reply.modelShortName)
        assertEquals("0.8s", viewModel.reply.latency)
    }

    @Test
    fun aSendInAnOpenedConversationContinuesIt() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Postgres index", model = Gemini)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))

        viewModel.send("And a GIN index?")
        runCurrent()

        assertEquals(conversation.id to "And a GIN index?", repository.sends.single())
        assertEquals(4, viewModel.state.messages.size)
        assertEquals("gemini-2.5-pro", viewModel.reply.modelShortName)
        assertTrue(viewModel.reply.thinking)
    }

    @Test
    fun anEntryWithAConversationIdOpensThatConversation() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Postgres index", model = Gemini)

        val state = chatViewModel(repository, ChatEntry(conversation.id)).state

        assertEquals<Long?>(conversation.id, state.conversationId)
        assertEquals(Gemini, state.model)
        assertEquals(2, state.messages.size)
    }

    @Test
    fun anEntryForAConversationThatIsGoneOpensANewChat() = runTest {
        val state = chatViewModel(entry = ChatEntry(conversationId = 99)).state

        assertTrue(state.showsEmptyState)
        assertEquals(ModelCatalogue.default, state.model)
        assertTrue(state.messages.isEmpty())
    }

    // History

    @Test
    fun theHistoryButtonOpensHistoryOverTheChatWhichStaysUnderneath() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("useDebounce hook")
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))

        viewModel.onOpenHistory()
        // A second tap before History shows opens it once.
        viewModel.onOpenHistory()

        assertEquals(listOf(ChatEntry(conversation.id), HistoryEntry), viewModel.navigator.navBackStack.toList())
        assertEquals<Long?>(conversation.id, viewModel.state.conversationId)
    }

    // Copy

    @Test
    fun copyHandsTheCodeToTheClipboardAndReadsCopiedFor1200Ms() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("useDebounce hook")
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        val key = BlockKey(viewModel.reply.id, ReplyCodeIndex)
        val clipboard = mutableListOf<String>()

        viewModel.onCopyCode(key) { clipboard += it }

        assertEquals(listOf("return () => clearTimeout(id);"), clipboard)
        assertEquals(key, viewModel.state.copiedBlock)
        advance(1199.milliseconds)
        assertEquals(key, viewModel.state.copiedBlock)
        advance(1.milliseconds)
        assertNull(viewModel.state.copiedBlock)
        // The prototype shows no toast for a copy: the header's "copied" is the feedback.
        assertNull(viewModel.state.toast)
    }

    @Test
    fun anotherCopyRestartsTheCopiedTime() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("useDebounce hook")
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        val key = BlockKey(viewModel.reply.id, ReplyCodeIndex)
        viewModel.onCopyCode(key) {}
        advance(1000.milliseconds)

        viewModel.onCopyCode(key) {}
        advance(1000.milliseconds)

        assertEquals(key, viewModel.state.copiedBlock)
        advance(200.milliseconds)
        assertNull(viewModel.state.copiedBlock)
    }

    @Test
    fun copyDoesNothingForABlockThatIsNotCode() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("useDebounce hook")
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        val clipboard = mutableListOf<String>()

        viewModel.onCopyCode(BlockKey(viewModel.reply.id, index = 0)) { clipboard += it }
        viewModel.onCopyCode(BlockKey(messageId = 99, index = ReplyCodeIndex)) { clipboard += it }

        assertTrue(clipboard.isEmpty())
        assertNull(viewModel.state.copiedBlock)
    }

    @Test
    fun aStreamingTickRedrawsOnlyTheReplyThatGrew() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("useDebounce hook")
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        viewModel.send(Question)
        advance(ThinkingTime)
        val before = viewModel.state.messages

        advance(TickTime)
        val after = viewModel.state.messages

        assertEquals(before.size, after.size)
        before.dropLast(1).zip(after.dropLast(1)).forEach { (old, new) -> assertSame(old, new) }
        assertEquals(2, viewModel.reply.blocks.size)
    }

    @Test
    fun theMessageCacheMapsEachUnchangedMessageOnce() {
        val cache = MessageUiCache()
        val user = Message.User(id = 1, conversationId = 1, text = Question, createdAt = Now)
        val first = cache.map(listOf(user), testTimes())

        val second = cache.map(listOf(user), testTimes())

        assertSame(first.single(), second.single())
        assertEquals(MessageUi.User(id = 1, time = "14:02", text = Question), second.single())
    }
}
