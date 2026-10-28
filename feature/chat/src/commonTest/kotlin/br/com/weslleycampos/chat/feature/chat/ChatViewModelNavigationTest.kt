package br.com.weslleycampos.chat.feature.chat

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.allStringResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val Title = "useDebounce hook with leading edge"

/**
 * The thread as History's and Voice's text view: its back chevron, the composer's mic, a "recent"
 * row and "⑂ fork here", on the back stacks Voice and History build: `[VoiceEntry, HistoryEntry,
 * ChatEntry(id)]` from a History row, `[VoiceEntry, ChatEntry]` from "Aa".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelNavigationTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** A chat on [entry] with [below] under it on the back stack. */
    private fun TestScope.chatViewModel(
        repository: FakeChatRepository,
        entry: ChatEntry,
        below: List<NavKey>,
    ): ChatViewModel {
        val viewModel = ChatViewModel(
            entry = entry,
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { addAll(below + entry) }),
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

    private val ChatViewModel.stack: List<NavKey> get() = navigator.navBackStack.toList()

    // Back

    @Test
    fun backReturnsToTheScreenUnderneathHistoryOrVoiceAsItWas() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed(Title)
        val entry = ChatEntry(conversation.id)
        val fromHistory = chatViewModel(repository, entry, below = listOf(VoiceEntry(), HistoryEntry))
        val fromText = chatViewModel(repository, entry, below = listOf(VoiceEntry(conversation.id)))
        val newChat = chatViewModel(repository, ChatEntry(), below = listOf(VoiceEntry()))

        fromHistory.onBack()
        fromText.onBack()
        newChat.onBack()

        assertEquals(listOf(VoiceEntry(), HistoryEntry), fromHistory.stack)
        assertEquals(listOf<NavKey>(VoiceEntry(conversation.id)), fromText.stack)
        assertEquals(listOf<NavKey>(VoiceEntry()), newChat.stack)
    }

    @Test
    fun backWithNothingUnderneathGivesWayToANewVoiceSession() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed(Title)
        val alone = chatViewModel(repository, ChatEntry(conversation.id), below = emptyList())
        val newChatAlone = chatViewModel(repository, ChatEntry(), below = emptyList())

        alone.onBack()
        newChatAlone.onBack()

        // Never a dead end: Voice, the home screen, replaces the chat.
        assertEquals(listOf<NavKey>(VoiceEntry()), alone.stack)
        assertEquals(listOf<NavKey>(VoiceEntry()), newChatAlone.stack)
    }

    // The mic

    @Test
    fun theMicGoesOnInVoiceWithTheConversationAsTheRootOfTheStack() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed(Title)
        val entry = ChatEntry(conversation.id)
        val fromHistory = chatViewModel(repository, entry, below = listOf(VoiceEntry(), HistoryEntry))
        val overAnotherSession = chatViewModel(
            repository,
            ChatEntry(conversation.id),
            below = listOf(VoiceEntry(conversationId = 99), HistoryEntry),
        )

        fromHistory.onOpenVoice()
        // A second tap before Voice shows changes nothing more.
        fromHistory.onOpenVoice()
        overAnotherSession.onOpenVoice()

        assertEquals(listOf<NavKey>(VoiceEntry(conversation.id)), fromHistory.stack)
        assertEquals(listOf<NavKey>(VoiceEntry(conversation.id)), overAnotherSession.stack)
    }

    @Test
    fun theMicInAThreadOpenedThroughTextReturnsToVoiceOnTheSameConversation() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed(Title)
        val entry = ChatEntry(conversation.id)
        val viewModel = chatViewModel(repository, entry, below = listOf(VoiceEntry(conversation.id)))

        viewModel.onOpenVoice()

        // The same key as the Voice underneath, so Navigation 3 keeps that entry and its session.
        assertEquals(listOf<NavKey>(VoiceEntry(conversation.id)), viewModel.stack)
    }

    @Test
    fun theMicWithNoConversationYetOpensANewVoiceSession() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        repository.seed(Title)
        val throughText = chatViewModel(repository, ChatEntry(), below = listOf(VoiceEntry()))
        val throughTextOnAnEndedSession = chatViewModel(repository, ChatEntry(), below = listOf(VoiceEntry(7)))

        throughText.onOpenVoice()
        throughTextOnAnEndedSession.onOpenVoice()

        assertEquals(listOf<NavKey>(VoiceEntry()), throughText.stack)
        assertEquals(listOf<NavKey>(VoiceEntry()), throughTextOnAnEndedSession.stack)
    }

    @Test
    fun theMicAfterANewChatsFirstSendGoesOnInVoiceWithTheConversationItCreated() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository, ChatEntry(), below = listOf(VoiceEntry()))
        viewModel.onDraftChange("Write a useDebounce hook in TS.")
        viewModel.onSend()
        runCurrent()
        val created = repository.conversations.value.single().id

        viewModel.onOpenVoice()

        assertEquals(listOf<NavKey>(VoiceEntry(created)), viewModel.stack)
    }

    // The empty state's "recent" rows

    @Test
    fun aRecentRowOverVoiceOpensInPlaceAndTheMicAndBackFollowIt() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val recent = repository.seed(Title)
        val viewModel = chatViewModel(repository, ChatEntry(), below = listOf(VoiceEntry()))
        assertEquals(listOf(recent.id), viewModel.uiState.value.recent.map { it.id })

        viewModel.onOpenRecent(recent.id)
        runCurrent()

        assertEquals(listOf(VoiceEntry(), ChatEntry()), viewModel.stack)
        assertEquals<Long?>(recent.id, viewModel.uiState.value.conversationId)
        assertEquals(Title, assertIs<MessageUi.User>(viewModel.uiState.value.messages.first()).text)
        viewModel.onOpenVoice()
        assertEquals(listOf<NavKey>(VoiceEntry(recent.id)), viewModel.stack)

        val again = chatViewModel(repository, ChatEntry(), below = listOf(VoiceEntry()))
        again.onOpenRecent(recent.id)
        again.onBack()
        assertEquals(listOf<NavKey>(VoiceEntry()), again.stack)
    }

    // Fork

    @Test
    fun forkHereReplacesTheThreadAndKeepsVoiceAndHistoryUnderIt() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val source = repository.seed(Title)
        val fromHistory = chatViewModel(repository, ChatEntry(source.id), below = listOf(VoiceEntry(), HistoryEntry))
        val fromText = chatViewModel(repository, ChatEntry(source.id), below = listOf(VoiceEntry(source.id)))

        fromHistory.messageActions.onFork(fromHistory.uiState.value.messages.first().id)
        runCurrent()
        val first = repository.conversations.value.last().id
        fromText.messageActions.onFork(fromText.uiState.value.messages.first().id)
        runCurrent()
        val second = repository.conversations.value.last().id

        assertEquals(listOf(VoiceEntry(), HistoryEntry, ChatEntry(first)), fromHistory.stack)
        assertEquals(listOf(VoiceEntry(source.id), ChatEntry(second)), fromText.stack)
    }

    // The controls

    @Test
    fun theBarHasBackAndTheComposerTheMicWithNoHistoryNewChatOrAttach() {
        val labels = FeatureChatRes.allStringResources.keys

        assertTrue("chat_back" in labels)
        assertTrue("chat_voice" in labels)
        assertTrue("chat_send" in labels)
        // Not built, or not on this screen any more: no label a control could be announced by.
        assertEquals(emptyList(), labels.filter { it in listOf("chat_history", "chat_new_chat", "chat_attach") })
    }
}
