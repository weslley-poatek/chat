package br.com.weslleycampos.chat.feature.chat

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.data.chat.models.ForkOrigin
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_fork_failed
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_forked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val Title = "useDebounce hook"
private const val FollowUp = "And with a leading edge?"

/** [ReplyBlocks] as "copy" copies them, the prototype's `msgText`. */
private const val ReplyText = "Two likely causes, in order of probability:\n\n" +
    "1. A stale value.\n2. A reassigned timeout id.\n\n" +
    "Minimal fix:\n\n" +
    "return () => clearTimeout(id);\n\n" +
    "If it still reads stale, log value inside the callback."

/**
 * The message action row on `ChatViewModel.messageActions`: selection, "copy", "⑂ fork here", the
 * fork banner, "read aloud" and its bar, and "retry".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelMessageActionsTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.chatViewModel(
        repository: FakeChatRepository,
        entry: ChatEntry,
        forks: FakeForkRepository = FakeForkRepository(repository),
        relay: ChatToastRelay = ChatToastRelay(),
    ): ChatViewModel {
        val viewModel = ChatViewModel(
            entry = entry,
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(entry) }),
            repository = repository,
            models = FakeModelRepository(),
            times = testTimes(),
            forks = forks,
            compares = FakeCompareRepository(repository),
            relay = relay,
        )
        runCurrent()
        return viewModel
    }

    /** A chat open on a seeded conversation: its question, then its done reply. */
    private fun TestScope.openChat(
        repository: FakeChatRepository = FakeChatRepository(backgroundScope),
    ): ChatViewModel =
        chatViewModel(repository, ChatEntry(repository.seed(Title).id))

    private val ChatViewModel.state: ChatUiState get() = uiState.value

    private val ChatViewModel.actions: MessageActions get() = messageActions

    private val ChatViewModel.question: MessageUi.User get() = assertIs(state.messages.first())

    private val ChatViewModel.reply: MessageUi.Assistant get() = assertIs(state.messages.last())

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    private fun ChatViewModel.send(text: String) {
        onDraftChange(text)
        onSend()
    }

    // Selection

    @Test
    fun aTapSelectsAMessageAndShowsItsActionsAndATapOnItAgainHidesThem() = runTest {
        val viewModel = openChat()

        viewModel.actions.onSelect(viewModel.reply.id)
        assertEquals(viewModel.reply.id, viewModel.state.selectedMessage)

        viewModel.actions.onSelect(viewModel.reply.id)
        assertNull(viewModel.state.selectedMessage)

        viewModel.actions.onSelect(viewModel.question.id)
        assertEquals(viewModel.question.id, viewModel.state.selectedMessage)
    }

    @Test
    fun aTapOnAnotherMessageMovesTheSelectionSoOneAtMostIsSelected() = runTest {
        val viewModel = openChat()
        viewModel.actions.onSelect(viewModel.question.id)

        viewModel.actions.onSelect(viewModel.reply.id)

        assertEquals(viewModel.reply.id, viewModel.state.selectedMessage)
        // A message that is not in the thread selects nothing.
        viewModel.actions.onSelect(99)
        assertEquals(viewModel.reply.id, viewModel.state.selectedMessage)
    }

    @Test
    fun aReplyThinkingOrStreamingCannotBeSelectedOrActedOn() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val forks = FakeForkRepository(repository)
        val viewModel = chatViewModel(repository, ChatEntry(repository.seed(Title).id), forks)
        viewModel.send(FollowUp)
        runCurrent()
        val replyId = viewModel.reply.id
        val clipboard = mutableListOf<String>()

        for (time in listOf(Duration.ZERO, ThinkingTime)) {
            advance(time)
            assertTrue(viewModel.reply.inProgress)
            viewModel.actions.onSelect(replyId)
            viewModel.actions.onCopy(replyId) { clipboard += it }
            viewModel.actions.onReadAloud(replyId)
            viewModel.actions.onFork(replyId)
            runCurrent()
            assertNull(viewModel.state.selectedMessage)
            assertNull(viewModel.state.readAloud)
        }
        assertTrue(clipboard.isEmpty())
        assertTrue(forks.forks.isEmpty())

        advance(ReplyTime - ThinkingTime)
        viewModel.actions.onSelect(replyId)
        assertEquals(replyId, viewModel.state.selectedMessage)
    }

    @Test
    fun aSelectionAndAReadStayWhileAnotherReplyStreams() = runTest {
        val viewModel = openChat()
        val firstReply = viewModel.reply.id
        viewModel.actions.onReadAloud(firstReply)
        viewModel.actions.onSelect(viewModel.question.id)

        viewModel.send(FollowUp)
        advance(ThinkingTime + TickTime)

        assertTrue(viewModel.reply.inProgress)
        assertEquals(viewModel.question.id, viewModel.state.selectedMessage)
        val reading = assertIs<ReadAloudUi>(viewModel.state.readAloud)
        assertEquals(firstReply to true, reading.messageId to reading.playing)
        assertEquals(((ThinkingTime + TickTime) / ReadAloudTick).toInt(), reading.ticks)
    }

    // Copy

    @Test
    fun copyHandsTheMessageTextToTheClipboardAndReadsCopiedFor1200Ms() = runTest {
        val viewModel = openChat()
        val clipboard = mutableListOf<String>()
        viewModel.actions.onSelect(viewModel.reply.id)

        viewModel.actions.onCopy(viewModel.reply.id) { clipboard += it }
        viewModel.actions.onCopy(viewModel.question.id) { clipboard += it }

        assertEquals(listOf(ReplyText, Title), clipboard)
        assertEquals(viewModel.question.id, viewModel.state.copiedMessage)
        // The row stays open, as the prototype's `copyAll` leaves the selection.
        assertEquals(viewModel.reply.id, viewModel.state.selectedMessage)
        advance(1199.milliseconds)
        assertEquals(viewModel.question.id, viewModel.state.copiedMessage)
        advance(1.milliseconds)
        assertNull(viewModel.state.copiedMessage)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun aMessageCopyAndACodeCopyShareOneCopiedLabelAndItsTime() = runTest {
        val viewModel = openChat()
        val code = BlockKey(viewModel.reply.id, ReplyCodeIndex)

        viewModel.actions.onCopy(viewModel.reply.id) {}
        advance(1000.milliseconds)
        viewModel.onCopyCode(code) {}

        assertEquals(code to null, viewModel.state.copiedBlock to viewModel.state.copiedMessage)
        advance(1000.milliseconds)
        viewModel.actions.onCopy(viewModel.reply.id) {}
        assertEquals(null to viewModel.reply.id, viewModel.state.copiedBlock to viewModel.state.copiedMessage)
        advance(1199.milliseconds)
        assertEquals(viewModel.reply.id, viewModel.state.copiedMessage)
        advance(1.milliseconds)
        assertNull(viewModel.state.copiedMessage)
    }

    // Fork

    @Test
    fun forkHereReplacesTheChatWithTheForkWhichSaysForkedNewConversationFor1400Ms() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val source = repository.seed(Title)
        val forks = FakeForkRepository(repository)
        val relay = ChatToastRelay()
        val viewModel = chatViewModel(repository, ChatEntry(source.id), forks, relay)
        val question = viewModel.question.id
        viewModel.actions.onSelect(question)

        viewModel.actions.onFork(question)
        // The row closes with the tap, and a second tap while it forks forks nothing more.
        assertNull(viewModel.state.selectedMessage)
        viewModel.actions.onFork(question)
        runCurrent()

        assertEquals(listOf(source.id to question), forks.forks)
        val fork = repository.conversations.value.last()
        assertEquals(listOf<NavKey>(ChatEntry(fork.id)), viewModel.navigator.navBackStack.toList())
        // The fork's entry has a ViewModel of its own, which shows the fork and the toast.
        val forked = chatViewModel(repository, ChatEntry(fork.id), forks, relay)
        assertEquals(listOf(Title), forked.state.messages.map { assertIs<MessageUi.User>(it).text })
        assertEquals("forked from “$Title” at message 1", forked.state.forkBanner)
        assertEquals(FeatureChatRes.string.chat_toast_forked, forked.state.toast?.message)
        advance(ChatToastDefaults.Duration - 1.milliseconds)
        assertEquals<StringResource?>(FeatureChatRes.string.chat_toast_forked, forked.state.toast?.message)
        advance(1.milliseconds)
        assertNull(forked.state.toast)
        // The toast is the fork's alone: the next chat entry shows none.
        assertNull(chatViewModel(repository, ChatEntry(fork.id), forks, relay).state.toast)
    }

    @Test
    fun aForkThatForksNothingOrFailsLeavesTheChatAndSaysSo() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val source = repository.seed(Title)
        val relay = ChatToastRelay()
        for (answer in listOf<suspend (Long, Long) -> Long?>({ _, _ -> null }, { _, _ -> error("disk full") })) {
            val forks = FakeForkRepository(repository).apply { this.answer = answer }
            val viewModel = chatViewModel(repository, ChatEntry(source.id), forks, relay)

            viewModel.actions.onFork(viewModel.reply.id)
            runCurrent()

            assertEquals(listOf(source.id to viewModel.reply.id), forks.forks)
            assertEquals(listOf<NavKey>(ChatEntry(source.id)), viewModel.navigator.navBackStack.toList())
            assertEquals(FeatureChatRes.string.chat_toast_fork_failed, viewModel.state.toast?.message)
            assertNull(relay.take())
            advance(ChatToastDefaults.Duration)
            assertNull(viewModel.state.toast)
        }
    }

    @Test
    fun theForkBannerSaysWhereTheForkCameFromUntilItsCrossHidesItForGood() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val fork = repository.seed(
            Title,
            fork = ForkOrigin(sourceTitle = "useDebounce hook with leading edge", messageNumber = 2),
        )
        val other = repository.seed("Postgres index")
        val forks = FakeForkRepository(repository)
        val viewModel = chatViewModel(repository, ChatEntry(fork.id), forks)

        assertEquals("forked from “useDebounce hook with leading edge” at message 2", viewModel.state.forkBanner)
        // It stays through a send in the fork, as the prototype's `forkFrom`.
        viewModel.send(FollowUp)
        advance(ReplyTime)
        assertEquals("forked from “useDebounce hook with leading edge” at message 2", viewModel.state.forkBanner)

        viewModel.actions.onDismissForkBanner()
        runCurrent()

        assertEquals(listOf(fork.id), forks.dismissals)
        assertNull(viewModel.state.forkBanner)
        assertNull(chatViewModel(repository, ChatEntry(fork.id), forks).state.forkBanner)
        assertNull(chatViewModel(repository, ChatEntry(other.id), forks).state.forkBanner)
    }

    @Test
    fun aForkGoneWhileOpenLeavesANewChatWithNoForkBanner() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val fork = repository.seed(
            Title,
            fork = ForkOrigin(sourceTitle = "useDebounce hook with leading edge", messageNumber = 2),
        )
        val viewModel = chatViewModel(repository, ChatEntry(fork.id))

        repository.delete(fork.id)
        runCurrent()

        assertTrue(viewModel.state.showsEmptyState)
        assertNull(viewModel.state.forkBanner)
    }

    // Read aloud

    @Test
    fun readAloudShowsTheBarUnderTheReplyFillingPointSixPercentEvery100MsUntilFullWithPlay() = runTest {
        val viewModel = openChat()
        val reply = viewModel.reply.id
        viewModel.actions.onSelect(reply)

        viewModel.actions.onReadAloud(reply)

        assertEquals(ReadAloudUi(messageId = reply, ticks = 0, playing = true), viewModel.state.readAloud)
        assertNull(viewModel.state.selectedMessage)
        advance(ReadAloudTick)
        assertEquals(0.006f, viewModel.state.readAloud?.progress)
        advance(ReadAloudTick * (ReadAloudTicks - 2))
        assertEquals(
            ReadAloudUi(messageId = reply, ticks = ReadAloudTicks - 1, playing = true),
            viewModel.state.readAloud,
        )
        advance(ReadAloudTick)
        val full = assertIs<ReadAloudUi>(viewModel.state.readAloud)
        assertEquals(ReadAloudUi(messageId = reply, ticks = ReadAloudTicks, playing = false), full)
        assertEquals(1f to "0:17 / 0:17", full.progress to full.label)
        // It stops full and stays until its "×".
        advance(10.seconds)
        assertEquals(full, viewModel.state.readAloud)
    }

    @Test
    fun playAndPauseHoldTheBarAndPlayOnAFinishedReadStartsItAgain() = runTest {
        val viewModel = openChat()
        val reply = viewModel.reply.id
        viewModel.actions.onReadAloud(reply)
        advance(ReadAloudTick * 5)

        viewModel.actions.player.onPlayPause()
        advance(1.seconds)
        assertEquals(ReadAloudUi(messageId = reply, ticks = 5, playing = false), viewModel.state.readAloud)

        viewModel.actions.player.onPlayPause()
        advance(ReadAloudTick)
        assertEquals(ReadAloudUi(messageId = reply, ticks = 6, playing = true), viewModel.state.readAloud)

        // "read aloud" on the reply being read plays or pauses it, and keeps its row open.
        viewModel.actions.onSelect(reply)
        viewModel.actions.onReadAloud(reply)
        assertEquals(ReadAloudUi(messageId = reply, ticks = 6, playing = false), viewModel.state.readAloud)
        assertEquals(reply, viewModel.state.selectedMessage)
        viewModel.actions.onReadAloud(reply)
        advance(ReadAloudTick * ReadAloudTicks)
        assertEquals(ReadAloudUi(messageId = reply, ticks = ReadAloudTicks, playing = false), viewModel.state.readAloud)

        viewModel.actions.player.onPlayPause()
        assertEquals(ReadAloudUi(messageId = reply, ticks = 0, playing = true), viewModel.state.readAloud)
    }

    @Test
    fun onlyOneReplyIsReadAtATime() = runTest {
        val viewModel = openChat()
        val first = viewModel.reply.id
        viewModel.send(FollowUp)
        advance(ReplyTime)
        val second = viewModel.reply.id
        viewModel.actions.onReadAloud(first)
        advance(ReadAloudTick * 5)

        viewModel.actions.onReadAloud(second)
        advance(ReadAloudTick)

        assertEquals(ReadAloudUi(messageId = second, ticks = 1, playing = true), viewModel.state.readAloud)
        // A user message has no "read aloud".
        viewModel.actions.onReadAloud(viewModel.question.id)
        assertEquals(second, viewModel.state.readAloud?.messageId)
    }

    @Test
    fun readingStopsOnItsCrossOnAnotherConversationAndWhenTheConversationIsGone() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed(Title)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        val reply = viewModel.reply.id

        viewModel.actions.onReadAloud(reply)
        viewModel.actions.player.onStop()
        advance(1.seconds)
        assertNull(viewModel.state.readAloud)

        viewModel.actions.onReadAloud(reply)
        viewModel.actions.onSelect(viewModel.question.id)
        repository.delete(conversation.id)
        runCurrent()
        assertTrue(viewModel.state.showsEmptyState)
        advance(1.seconds)
        assertNull(viewModel.state.readAloud)
        assertNull(viewModel.state.selectedMessage)

        val other = repository.seed("Postgres index")
        val recent = chatViewModel(repository, ChatEntry(repository.seed(Title).id))
        recent.actions.onReadAloud(recent.reply.id)
        recent.actions.onSelect(recent.question.id)
        recent.onOpenRecent(other.id)
        advance(1.seconds)
        assertEquals(other.id, recent.state.conversationId)
        assertNull(recent.state.readAloud)
        assertNull(recent.state.selectedMessage)
    }

    @Test
    fun leavingTheScreenPausesTheReadAndClosesTheActionRow() = runTest {
        val viewModel = openChat()
        val reply = viewModel.reply.id
        viewModel.actions.onReadAloud(reply)
        advance(ReadAloudTick * 3)
        viewModel.actions.onSelect(viewModel.question.id)

        viewModel.actions.onLeave()
        advance(1.seconds)

        assertEquals(ReadAloudUi(messageId = reply, ticks = 3, playing = false), viewModel.state.readAloud)
        assertNull(viewModel.state.selectedMessage)
        viewModel.actions.player.onPlayPause()
        advance(ReadAloudTick)
        assertEquals(ReadAloudUi(messageId = reply, ticks = 4, playing = true), viewModel.state.readAloud)
    }

    // Retry

    @Test
    fun retryPutsTheUserMessageInTheComposerInPlaceOfTheDraftWithoutSendingIt() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository, ChatEntry(repository.seed(Title).id))
        viewModel.onDraftChange("half a thought")
        viewModel.actions.onSelect(viewModel.question.id)

        viewModel.actions.onRetry(viewModel.question.id)
        runCurrent()

        assertEquals(Title, viewModel.state.draft)
        assertNull(viewModel.state.selectedMessage)
        assertTrue(repository.sends.isEmpty())
        // A reply has no "retry".
        viewModel.onDraftChange("")
        viewModel.actions.onRetry(viewModel.reply.id)
        assertEquals("", viewModel.state.draft)
    }

    // Row 10's auto read

    @Test
    fun aReplyThatFinishesStartsNoReadWhileAutoReadIsOff() = runTest {
        val viewModel = openChat()

        viewModel.send(FollowUp)
        advance(ReplyTime)

        assertFalse(viewModel.reply.inProgress)
        assertNull(viewModel.state.readAloud)
    }

    @Test
    fun theReplyDoneHookReadsAReplyThatJustFinishedWhenAutoReadIsOn() = runTest {
        val user = Message.User(id = 1, conversationId = 1, text = Title, createdAt = Now)
        val thinking = Message.Assistant(
            id = 2,
            conversationId = 1,
            model = ModelCatalogue.default,
            blocks = emptyList(),
            status = ReplyStatus.THINKING,
            latency = null,
            createdAt = Now,
        )
        val done = thinking.copy(blocks = ReplyBlocks, status = ReplyStatus.DONE, latency = ReplyLatency)
        for (autoRead in listOf(false, true)) {
            val state = MutableStateFlow(
                ChatUiState(conversationId = 1, messages = listOf(user, done).map { it.toUi(testTimes()) }),
            )
            val actions = MessageActions(
                scope = backgroundScope,
                state = state,
                forks = FakeForkRepository(),
                navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(ChatEntry(1)) }),
                copied = TimedValue(backgroundScope, ChatViewModel.CopiedDuration) {},
                toast = TimedValue(backgroundScope, ChatToastDefaults.Duration) {},
                relay = ChatToastRelay(),
                autoRead = { autoRead },
            )
            // A thread that opens on a done reply reads nothing; one whose reply finishes does.
            actions.onThread(listOf(user, done))
            assertNull(state.value.readAloud)

            actions.onThread(listOf(user, thinking))
            actions.onThread(listOf(user, done))

            assertEquals(ReadAloudUi(messageId = 2).takeIf { autoRead }, state.value.readAloud)
        }
    }

    @Test
    fun aReplyHasJustFinishedOnlyWhenItWasOnItsWayAndIsNowDone() {
        val user = Message.User(id = 1, conversationId = 1, text = Title, createdAt = Now)
        fun reply(status: ReplyStatus) = Message.Assistant(
            id = 2,
            conversationId = 1,
            model = ModelCatalogue.default,
            blocks = ReplyBlocks,
            status = status,
            latency = null,
            createdAt = Now,
        )

        assertEquals(
            2L,
            finishedReply(listOf(user, reply(ReplyStatus.THINKING)), listOf(user, reply(ReplyStatus.DONE))),
        )
        assertEquals(
            2L,
            finishedReply(listOf(user, reply(ReplyStatus.STREAMING)), listOf(user, reply(ReplyStatus.DONE))),
        )
        assertNull(
            finishedReply(listOf(user, reply(ReplyStatus.STREAMING)), listOf(user, reply(ReplyStatus.INTERRUPTED))),
        )
        assertNull(
            finishedReply(listOf(user, reply(ReplyStatus.STREAMING)), listOf(user, reply(ReplyStatus.STREAMING))),
        )
        assertNull(finishedReply(listOf(user, reply(ReplyStatus.DONE)), listOf(user, reply(ReplyStatus.DONE))))
        assertNull(finishedReply(emptyList(), listOf(user, reply(ReplyStatus.DONE))))
        assertNull(finishedReply(listOf(user), listOf(user)))
    }
}
