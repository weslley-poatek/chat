package br.com.weslleycampos.chat.feature.chat

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerMark
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerUiState
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_comparing
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_continue_failed
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_continuing
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

private const val Question = "My debounced value is one render behind. Why?"

/**
 * Compare mode on `ChatViewModel`: the picker's compare button and pick rules, a send answered by a
 * pair, its cards, and "continue with this →".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCompareTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.chatViewModel(
        repository: FakeChatRepository = FakeChatRepository(backgroundScope),
        entry: ChatEntry = ChatEntry(),
        compares: FakeCompareRepository = FakeCompareRepository(repository),
        forks: FakeForkRepository = FakeForkRepository(repository),
    ): ChatViewModel {
        val viewModel = ChatViewModel(
            entry = entry,
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(entry) }),
            repository = repository,
            models = FakeModelRepository(),
            times = testTimes(),
            forks = forks,
            compares = compares,
            relay = ChatToastRelay(),
        )
        runCurrent()
        return viewModel
    }

    private val ChatViewModel.state: ChatUiState get() = uiState.value

    private val ChatViewModel.pair: MessageUi.ComparePair get() = assertIs(state.messages.last())

    /** The keys of the rows that carry "◐". */
    private val ModelPickerUiState.compared: List<String>
        get() = (favorites + personas + allModels).filter { it.mark == ModelPickerMark.Compare }.map { it.key }

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    private fun ChatViewModel.send(text: String) {
        onDraftChange(text)
        onSend()
    }

    private val sonnet = ModelCatalogue.default
    private val comparing = ModelChoice(sonnet, compareOn = true, compareWith = Gpt5)

    // The compare button

    @Test
    fun theCompareButtonTurnsCompareOnAndOffInAnOpenConversationKeepingTheSheetOpen() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed(Question)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        viewModel.onOpenModelPicker()

        viewModel.compareActions.onToggleCompare()
        runCurrent()

        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(ModelChoice(sonnet, compareOn = true), viewModel.state.choice)
        assertTrue(viewModel.state.modelPicker.compareOn)
        assertEquals("pick a second model to run beside sonnet-4.5", viewModel.state.modelPicker.compareHint)
        // Before a second model is picked, the chat still answers with one.
        assertEquals("Claude Sonnet 4.5", viewModel.state.chipLabel)
        assertEquals("message sonnet-4.5…", viewModel.state.composerPlaceholder)
        assertEquals(conversation.id to ModelChoice(sonnet, compareOn = true), repository.chooses.last())
        assertTrue(repository.conversations.value.single().compareOn)
        assertNull(viewModel.state.toast)

        viewModel.compareActions.onToggleCompare()
        runCurrent()

        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(ModelChoice(sonnet), viewModel.state.choice)
        assertFalse(viewModel.state.modelPicker.compareOn)
        assertNull(viewModel.state.modelPicker.compareHint)
        assertFalse(repository.conversations.value.single().compareOn)
    }

    @Test
    fun theCompareButtonDropsAPersonaAndTurningCompareOffDropsTheSecondModel() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository, ChatEntry(repository.seed(Question, persona = Reviewer).id))

        viewModel.compareActions.onToggleCompare()
        runCurrent()
        assertEquals(ModelChoice(Reviewer.model, compareOn = true), viewModel.state.choice)

        viewModel.onPick(ModelChoice(Gpt5))
        runCurrent()
        viewModel.compareActions.onToggleCompare()
        runCurrent()

        assertEquals(ModelChoice(Reviewer.model), viewModel.state.choice)
        assertNull(repository.conversations.value.single().compareWith)
    }

    @Test
    fun aRefusedToggleShowsTheConversationsOwnChoiceAgain() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        repository.chooseAnswer = { _, _ -> false }
        val viewModel = chatViewModel(repository, ChatEntry(repository.seed(Question, model = Gemini).id))

        viewModel.compareActions.onToggleCompare()
        assertTrue(viewModel.state.choice.compareOn)
        runCurrent()

        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
        assertFalse(viewModel.state.modelPicker.compareOn)
    }

    // The pick rules

    @Test
    fun pickingTheSecondModelClosesTheSheetSavesItAndToastsComparing() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed(Question)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        viewModel.onOpenModelPicker()
        viewModel.compareActions.onToggleCompare()

        viewModel.onPick(ModelChoice(Gpt5))
        runCurrent()

        assertFalse(viewModel.state.modelPickerOpen)
        assertEquals(comparing, viewModel.state.choice)
        assertEquals(conversation.id to comparing, repository.chooses.last())
        assertEquals(Gpt5, repository.conversations.value.single().comparing)
        assertEquals(
            ChatToastUi(FeatureChatRes.string.chat_toast_comparing, listOf("sonnet-4.5", "gpt-5")),
            viewModel.state.toast,
        )
        assertEquals("sonnet-4.5 ⇄ gpt-5", viewModel.state.chipLabel)
        assertEquals("ask sonnet-4.5 and gpt-5…", viewModel.state.composerPlaceholder)
        assertEquals(listOf("favorite:gpt5", "model:gpt5"), viewModel.state.modelPicker.compared)
        assertEquals(
            "sonnet-4.5 ⇄ gpt-5 · tap another model to swap the second",
            viewModel.state.modelPicker.compareHint,
        )
        // The chat keeps the first model's accent.
        assertEquals(sonnet, viewModel.state.model)

        // Another model row swaps the second.
        viewModel.onOpenModelPicker()
        viewModel.onPick(ModelChoice(Gemini))
        runCurrent()

        assertEquals(ModelChoice(sonnet, compareOn = true, compareWith = Gemini), viewModel.state.choice)
        assertEquals(listOf("sonnet-4.5", "gemini-2.5-pro"), viewModel.state.toast?.args)
        assertFalse(viewModel.state.modelPickerOpen)
    }

    @Test
    fun whileComparingTheFirstModelsOwnRowChangesNothingAndTheSheetStaysOpen() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository, ChatEntry(repository.seed(Question, comparing = Gpt5).id))
        val chooses = repository.chooses.size
        viewModel.onOpenModelPicker()

        viewModel.onPick(ModelChoice(sonnet))
        runCurrent()

        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(comparing, viewModel.state.choice)
        assertEquals(chooses, repository.chooses.size)
        assertNull(viewModel.state.toast)

        // Before a second model is picked, too.
        viewModel.compareActions.onToggleCompare()
        viewModel.compareActions.onToggleCompare()
        runCurrent()
        assertEquals(ModelChoice(sonnet, compareOn = true), viewModel.state.choice)
        viewModel.onPick(ModelChoice(sonnet))
        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(ModelChoice(sonnet, compareOn = true), viewModel.state.choice)
    }

    @Test
    fun aPersonaRowTurnsCompareOffWithoutTheComparingToast() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository, ChatEntry(repository.seed(Question, comparing = Gpt5).id))
        viewModel.onOpenModelPicker()

        viewModel.onPick(ModelChoice(Terse))
        runCurrent()

        assertFalse(viewModel.state.modelPickerOpen)
        assertEquals(ModelChoice(Terse), viewModel.state.choice)
        assertEquals("Terse · gpt-5", viewModel.state.chipLabel)
        assertFalse(repository.conversations.value.single().compareOn)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun inANewChatCompareIsKeptForItsFirstSendWhichAPairAnswers() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository)
        viewModel.onOpenModelPicker()

        viewModel.compareActions.onToggleCompare()
        viewModel.onPick(ModelChoice(Gpt5))
        runCurrent()

        assertEquals(comparing, viewModel.state.choice)
        assertEquals("sonnet-4.5 ⇄ gpt-5", viewModel.state.chipLabel)
        assertEquals(listOf("sonnet-4.5", "gpt-5"), viewModel.state.toast?.args)
        assertTrue(viewModel.state.showsEmptyState)
        assertTrue(repository.chooses.isEmpty())

        viewModel.send(Question)
        runCurrent()

        assertEquals(listOf<ModelChoice?>(comparing), repository.newChatChoices)
        assertEquals(Gpt5, repository.conversations.value.single().comparing)
        assertEquals(listOf("sonnet-4.5", "gpt-5"), viewModel.pair.sides.map { it.modelShortName })
        assertEquals(comparing, viewModel.state.choice)
    }

    @Test
    fun aComparingConversationGoneWhileOpenGoesBackToANewChatWithCompareOff() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val compared = repository.seed(Question, comparing = Gpt5)
        val viewModel = chatViewModel(repository, ChatEntry(compared.id))

        repository.delete(compared.id)
        runCurrent()

        assertEquals(ModelChoice(sonnet), viewModel.state.choice)
        assertEquals("Claude Sonnet 4.5", viewModel.state.chipLabel)
        assertEquals("message sonnet-4.5…", viewModel.state.composerPlaceholder)
        viewModel.send(Question)
        runCurrent()
        assertEquals(ModelChoice(sonnet), repository.newChatChoices.last())
        assertIs<MessageUi.Assistant>(viewModel.state.messages.last())
    }

    @Test
    fun aComparingConversationOpensComparingFromItsEntryOrRecent() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val compared = repository.seed(Question, model = Gemini, comparing = Gpt5)
        val viewModel = chatViewModel(repository, ChatEntry(compared.id))

        assertEquals(ModelChoice(Gemini, compareOn = true, compareWith = Gpt5), viewModel.state.choice)
        assertEquals("gemini-2.5-pro ⇄ gpt-5", viewModel.state.chipLabel)
        assertEquals("ask gemini-2.5-pro and gpt-5…", viewModel.state.composerPlaceholder)
        assertTrue(viewModel.state.modelPicker.compareOn)
        assertEquals(listOf("favorite:gpt5", "model:gpt5"), viewModel.state.modelPicker.compared)
        assertEquals(Gemini, viewModel.state.model)

        val other = repository.seed("Postgres index")
        viewModel.onOpenRecent(other.id)
        runCurrent()
        assertEquals(ModelChoice(sonnet), viewModel.state.choice)
        viewModel.onOpenRecent(compared.id)
        runCurrent()
        assertEquals("gemini-2.5-pro ⇄ gpt-5", viewModel.state.chipLabel)
    }

    // A pair

    @Test
    fun aSendWhileComparingIsAnsweredByAPairWhoseCardsFinishOnTheirOwn() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository, ChatEntry(repository.seed(Question, comparing = Gpt5).id))

        viewModel.send("And with a leading edge?")
        runCurrent()

        val thinking = viewModel.pair
        assertEquals(listOf(0, 1), thinking.sides.map { it.index })
        assertEquals(listOf("Claude Sonnet 4.5", "GPT-5"), thinking.sides.map { it.modelName })
        assertEquals(listOf(ChatPalette.SONNET, ChatPalette.GPT5), thinking.sides.map { it.palette })
        assertTrue(thinking.sides.all { it.thinking && it.inProgress && !it.done })
        assertTrue(viewModel.state.replyInProgress)

        advance(ThinkingTime)
        assertTrue(viewModel.pair.sides.all { it.inProgress && !it.thinking && it.blocks.size == 1 })

        advance(AltTime - ThinkingTime)
        val oneDone = viewModel.pair
        assertEquals(listOf(false, true), oneDone.sides.map { it.done })
        assertEquals(listOf(null, "1.9s"), oneDone.sides.map { it.latency })
        assertEquals(AltBlocks.size, oneDone.sides[1].blocks.size)
        assertTrue(viewModel.state.replyInProgress)

        advance(ReplyTime - AltTime)
        val done = viewModel.pair
        assertTrue(done.sides.all { it.done && !it.inProgress })
        assertEquals(listOf("0.8s", "1.9s"), done.sides.map { it.latency })
        assertEquals(ReplyBlocks.size, done.sides[0].blocks.size)
        assertFalse(viewModel.state.replyInProgress)
        assertEquals(comparing, viewModel.state.choice)
    }

    @Test
    fun sendWaitsWhileEitherCardIsOnItsWay() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository, ChatEntry(repository.seed(Question, comparing = Gpt5).id))
        viewModel.send("And with a leading edge?")
        runCurrent()

        for (time in listOf(Duration.ZERO, ThinkingTime, AltTime - ThinkingTime)) {
            advance(time)
            viewModel.onDraftChange("And a trailing one?")
            assertFalse(viewModel.state.canSend)
            viewModel.onSend()
            runCurrent()
            assertEquals(1, repository.sends.size)
        }
        assertTrue(viewModel.pair.sides[1].done)

        advance(ReplyTime - AltTime)
        assertTrue(viewModel.state.canSend)
        viewModel.onSend()
        runCurrent()
        assertEquals(2, repository.sends.size)
    }

    @Test
    fun aPairIsNeitherSelectedNorReadAloudNorForkedNorCopied() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val forks = FakeForkRepository(repository)
        val entry = ChatEntry(repository.seed(Question, comparing = Gpt5).id)
        val viewModel = chatViewModel(repository, entry, forks = forks)
        val pairId = viewModel.pair.id
        var copied: String? = null

        viewModel.messageActions.onSelect(pairId)
        viewModel.messageActions.onReadAloud(pairId)
        viewModel.messageActions.onFork(pairId)
        viewModel.messageActions.onCopy(pairId) { copied = it }
        runCurrent()

        assertFalse(viewModel.pair.selectable)
        assertNull(viewModel.state.selectedMessage)
        assertNull(viewModel.state.readAloud)
        assertTrue(forks.forks.isEmpty())
        assertNull(copied)
        assertNull(viewModel.state.copiedMessage)
        // The question before it still is.
        val question = viewModel.state.messages.first()
        viewModel.messageActions.onSelect(question.id)
        assertEquals(question.id, viewModel.state.selectedMessage)
    }

    @Test
    fun aPairThatFinishesIsNotReadAloudEvenWithAutoReadOn() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed(Question, comparing = Gpt5)
        val (user, done) = repository.messages.value.getValue(conversation.id)
        val pair = assertIs<Message.ComparePair>(done)
        val thinking = pair.copy(
            sides = pair.sides.map { it.copy(blocks = emptyList(), status = ReplyStatus.THINKING, latency = null) },
        )
        val state = MutableStateFlow(ChatUiState(conversationId = conversation.id))
        val actions = MessageActions(
            scope = backgroundScope,
            state = state,
            forks = FakeForkRepository(),
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(ChatEntry(conversation.id)) }),
            copied = TimedValue(backgroundScope, ChatViewModel.CopiedDuration) {},
            toast = TimedValue(backgroundScope, ChatToastDefaults.Duration) {},
            relay = ChatToastRelay(),
            autoRead = { true },
        )

        actions.onThread(listOf(user, thinking))
        actions.onThread(listOf(user, pair))
        runCurrent()

        assertNull(state.value.readAloud)
        assertNull(finishedReply(previous = listOf(user, thinking), current = listOf(user, pair)))
    }

    // "continue with this →"

    @Test
    fun continuingWithACardSwitchesTheChatToItsModelAloneAndToasts() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val compares = FakeCompareRepository(repository)
        val conversation = repository.seed(Question, comparing = Gpt5)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id), compares = compares)
        val pairId = viewModel.pair.id

        viewModel.compareActions.onContinueWith(pairId = pairId, side = 1)
        runCurrent()

        assertEquals(listOf(Triple(conversation.id, pairId, 1)), compares.continues)
        assertEquals(
            ChatToastUi(FeatureChatRes.string.chat_toast_continuing, listOf("gpt-5")),
            viewModel.state.toast,
        )
        assertEquals(ModelChoice(Gpt5), viewModel.state.choice)
        assertEquals("GPT-5", viewModel.state.chipLabel)
        assertEquals(Gpt5, viewModel.state.model)
        // The pair stays as it was, both cards done.
        assertTrue(viewModel.pair.sides.all { it.done })

        viewModel.send("And with a leading edge?")
        runCurrent()
        assertEquals("gpt-5", assertIs<MessageUi.Assistant>(viewModel.state.messages.last()).modelShortName)
    }

    @Test
    fun aContinueThatChangesNothingOrFailsToastsTryAgainAndKeepsComparing() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val compares = FakeCompareRepository(repository)
        val entry = ChatEntry(repository.seed(Question, comparing = Gpt5).id)
        val viewModel = chatViewModel(repository, entry, compares = compares)
        val pairId = viewModel.pair.id

        for (answer in listOf<suspend () -> Boolean>({ false }, { error("database is closed") })) {
            compares.answer = { _, _, _ -> answer() }
            viewModel.compareActions.onContinueWith(pairId = pairId, side = 0)
            runCurrent()

            assertEquals(ChatToastUi(FeatureChatRes.string.chat_toast_continue_failed), viewModel.state.toast)
            assertEquals(comparing, viewModel.state.choice)
        }
        assertEquals(2, compares.continues.size)
    }

    @Test
    fun onlyADoneCardCanBeContinuedWithAndOnlyOnceAtATime() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val compares = FakeCompareRepository(repository)
        val entry = ChatEntry(repository.seed(Question, comparing = Gpt5).id)
        val viewModel = chatViewModel(repository, entry, compares = compares)
        viewModel.send("And with a leading edge?")
        advance(AltTime)
        val pairId = viewModel.pair.id

        // The first card still streams; the second is done. No such pair or card: nothing.
        viewModel.compareActions.onContinueWith(pairId = pairId, side = 0)
        viewModel.compareActions.onContinueWith(pairId = pairId + 10, side = 1)
        viewModel.compareActions.onContinueWith(pairId = pairId, side = 2)
        runCurrent()
        assertTrue(compares.continues.isEmpty())
        assertNull(viewModel.state.toast)

        val answer = CompletableDeferred<Boolean>()
        compares.answer = { _, _, _ -> answer.await() }
        viewModel.compareActions.onContinueWith(pairId = pairId, side = 1)
        viewModel.compareActions.onContinueWith(pairId = pairId, side = 1)
        runCurrent()
        assertEquals(1, compares.continues.size)

        answer.complete(true)
        runCurrent()
        assertEquals(listOf("gpt-5"), viewModel.state.toast?.args)
    }

    @Test
    fun aNewChatHasNothingToContinueWith() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val compares = FakeCompareRepository(repository)
        val viewModel = chatViewModel(repository, compares = compares)

        viewModel.compareActions.onContinueWith(pairId = 1, side = 0)
        runCurrent()

        assertTrue(compares.continues.isEmpty())
        assertNull(viewModel.state.toast)
    }
}
