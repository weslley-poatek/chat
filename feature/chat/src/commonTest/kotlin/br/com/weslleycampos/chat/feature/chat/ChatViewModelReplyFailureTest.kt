package br.com.weslleycampos.chat.feature.chat

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_failed
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_key_refused
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_model_unavailable
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_no_key
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_rate_limited
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_reply_unreachable
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
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val Question = "My debounced value is one render behind. Why?"

/**
 * A reply Fuelix could not give, on `ChatViewModel`: the toast that says why shows for the open
 * conversation only, a new chat's first reply included when it fails before its send returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelReplyFailureTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.chatViewModel(
        repository: FakeChatRepository = FakeChatRepository(backgroundScope),
        entry: ChatEntry = ChatEntry(),
    ): ChatViewModel {
        val viewModel = ChatViewModel(
            entry = entry,
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(entry) }),
            repository = repository,
            models = FakeModelRepository(),
            times = testTimes(),
            forks = FakeForkRepository(repository),
            compares = FakeCompareRepository(repository),
            relay = ChatToastRelay(),
        )
        runCurrent()
        return viewModel
    }

    private val ChatViewModel.state: ChatUiState get() = uiState.value

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    private fun ChatViewModel.send(text: String) {
        onDraftChange(text)
        onSend()
    }

    private fun toast(failure: ReplyFailure) = ChatToastUi(failure.toast)

    @Test
    fun everyFailureHasTheToastThatSaysWhy() {
        assertEquals(
            listOf(
                FeatureChatRes.string.chat_toast_reply_no_key,
                FeatureChatRes.string.chat_toast_reply_key_refused,
                FeatureChatRes.string.chat_toast_reply_model_unavailable,
                FeatureChatRes.string.chat_toast_reply_rate_limited,
                FeatureChatRes.string.chat_toast_reply_unreachable,
                FeatureChatRes.string.chat_toast_reply_failed,
            ),
            ReplyFailure.entries.map { it.toast },
        )
    }

    @Test
    fun aReplyOfTheOpenConversationThatFailsShowsWhyFor1400MsAndReadsInterrupted() = runTest {
        val repository = FakeChatRepository(backgroundScope).apply { replyFailure = ReplyFailure.NO_KEY }
        val conversation = repository.seed("Postgres index")
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))

        viewModel.send(Question)
        advance(ThinkingTime - 1.milliseconds)
        assertNull(viewModel.state.toast)
        advance(1.milliseconds)

        assertEquals(toast(ReplyFailure.NO_KEY), viewModel.state.toast)
        assertTrue(assertIs<MessageUi.Assistant>(viewModel.state.messages.last()).interrupted)
        // The message stays sent, and the next one can go.
        assertEquals(4, viewModel.state.messages.size)
        viewModel.onDraftChange("Try again")
        assertTrue(viewModel.state.canSend)
        advance(ChatToastDefaults.Duration - 1.milliseconds)
        assertEquals(toast(ReplyFailure.NO_KEY), viewModel.state.toast)
        advance(1.milliseconds)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun aNewChatsFirstReplyThatFailsAfterTheSendReturnedShowsWhy() = runTest {
        val repository = FakeChatRepository(backgroundScope).apply { replyFailure = ReplyFailure.UNREACHABLE }
        val viewModel = chatViewModel(repository)

        viewModel.send(Question)
        runCurrent()
        assertEquals<Long?>(1L, viewModel.state.conversationId)
        advance(ThinkingTime)

        assertEquals(toast(ReplyFailure.UNREACHABLE), viewModel.state.toast)
    }

    @Test
    fun aComparePairWhoseSidesBothFailShowsWhyAndBothSidesReadInterrupted() = runTest {
        val repository = FakeChatRepository(backgroundScope).apply { replyFailure = ReplyFailure.RATE_LIMITED }
        val conversation = repository.seed("Postgres index", comparing = Gpt5)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))

        viewModel.send(Question)
        advance(ThinkingTime)

        assertEquals(toast(ReplyFailure.RATE_LIMITED), viewModel.state.toast)
        val pair = assertIs<MessageUi.ComparePair>(viewModel.state.messages.last())
        assertEquals(listOf(true, true), pair.sides.map { it.interrupted })
        assertFalse(viewModel.state.replyInProgress)
    }

    @Test
    fun aFailureOfAnotherConversationShowsNothing() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val open = repository.seed("Postgres index")
        val other = repository.seed("useDebounce hook")
        val viewModel = chatViewModel(repository, ChatEntry(open.id))

        repository.fail(other.id, ReplyFailure.KEY_REFUSED)
        runCurrent()
        assertNull(viewModel.state.toast)

        // Once that conversation is open, its failures show.
        viewModel.onOpenRecent(other.id)
        runCurrent()
        repository.fail(other.id, ReplyFailure.KEY_REFUSED)
        runCurrent()
        assertEquals(toast(ReplyFailure.KEY_REFUSED), viewModel.state.toast)
    }

    @Test
    fun aFailureOfAnotherConversationShowsNothingInANewChatWithNothingSent() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val other = repository.seed("useDebounce hook")
        val viewModel = chatViewModel(repository)

        repository.fail(other.id, ReplyFailure.FAILED)
        runCurrent()

        assertNull(viewModel.state.toast)
    }

    @Test
    fun aNewChatsFirstReplyThatFailsBeforeItsSendReturnsShowsWhyOnceTheSendDoes() = runTest {
        val returned = CompletableDeferred<Unit>()
        val repository = FakeChatRepository(backgroundScope)
        repository.answer = { _, _ ->
            val id = repository.seed(Question).id
            // Fuelix refuses the key before the send has handed the conversation's id back.
            repository.fail(id, ReplyFailure.KEY_REFUSED)
            returned.await()
            id
        }
        val viewModel = chatViewModel(repository)

        viewModel.send(Question)
        runCurrent()
        assertTrue(viewModel.state.sending)
        assertNull(viewModel.state.conversationId)
        assertNull(viewModel.state.toast)

        returned.complete(Unit)
        runCurrent()
        assertEquals<Long?>(1L, viewModel.state.conversationId)
        assertEquals(toast(ReplyFailure.KEY_REFUSED), viewModel.state.toast)
        advance(ChatToastDefaults.Duration)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun aFailureOfAnotherConversationWhileANewChatSendsIsDroppedWhenTheSendReturns() = runTest {
        val returned = CompletableDeferred<Unit>()
        val repository = FakeChatRepository(backgroundScope)
        val other = repository.seed("useDebounce hook")
        repository.answer = { _, _ ->
            repository.fail(other.id, ReplyFailure.UNREACHABLE)
            returned.await()
            repository.seed(Question).id
        }
        val viewModel = chatViewModel(repository)

        viewModel.send(Question)
        runCurrent()
        returned.complete(Unit)
        runCurrent()

        val created = repository.conversations.value.single { it.title == Question }
        assertEquals<Long?>(created.id, viewModel.state.conversationId)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun aNewChatsOwnFailureStillShowsWhenAnotherConversationFailsBeforeItsSendReturns() = runTest {
        val returned = CompletableDeferred<Unit>()
        val repository = FakeChatRepository(backgroundScope)
        val other = repository.seed("useDebounce hook")
        repository.answer = { _, _ ->
            val id = repository.seed(Question).id
            repository.fail(id, ReplyFailure.KEY_REFUSED)
            repository.fail(other.id, ReplyFailure.UNREACHABLE)
            returned.await()
            id
        }
        val viewModel = chatViewModel(repository)

        viewModel.send(Question)
        runCurrent()
        returned.complete(Unit)
        runCurrent()

        assertEquals(toast(ReplyFailure.KEY_REFUSED), viewModel.state.toast)
    }

    @Test
    fun aFailureBeforeASendThatSendsNothingIsDroppedForTheSendFailedToast() = runTest {
        val returned = CompletableDeferred<Long?>()
        val repository = FakeChatRepository(backgroundScope)
        repository.answer = { _, _ ->
            repository.fail(1, ReplyFailure.NO_KEY)
            returned.await()
        }
        val viewModel = chatViewModel(repository)

        viewModel.send(Question)
        runCurrent()
        returned.complete(null)
        runCurrent()

        assertEquals(FeatureChatRes.string.chat_toast_send_failed, viewModel.state.toast?.message)
        assertEquals(Question, viewModel.state.draft)
    }

    @Test
    fun aNewFailureToastReplacesTheShownOneAndRestartsItsTime() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Postgres index")
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        repository.fail(conversation.id, ReplyFailure.UNREACHABLE)
        runCurrent()
        advance(1000.milliseconds)

        repository.fail(conversation.id, ReplyFailure.RATE_LIMITED)
        runCurrent()
        advance(1000.milliseconds)

        assertEquals(toast(ReplyFailure.RATE_LIMITED), viewModel.state.toast)
        advance(400.milliseconds)
        assertNull(viewModel.state.toast)
    }
}
