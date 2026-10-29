package br.com.weslleycampos.chat.feature.chat

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerMark
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerUiState
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

private const val Question = "My debounced value is one render behind. Why?"

/** The Model picker on `ChatViewModel`: the sheet, the pick rules, favorites, marks and the default model. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelPickerTest {
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
            forks = FakeForkRepository(repository),
            compares = FakeCompareRepository(repository),
            relay = ChatToastRelay(),
        )
        runCurrent()
        return viewModel
    }

    private val ChatViewModel.state: ChatUiState get() = uiState.value

    private val ChatViewModel.reply: MessageUi.Assistant get() = assertIs(state.messages.last())

    private val ChatViewModel.replies: List<MessageUi.Assistant>
        get() = state.messages.filterIsInstance<MessageUi.Assistant>()

    /** The keys of the rows that carry "●". */
    private val ModelPickerUiState.marked: List<String>
        get() = (favorites + personas + allModels).filter { it.mark == ModelPickerMark.Active }.map { it.key }

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    private fun ChatViewModel.send(text: String) {
        onDraftChange(text)
        onSend()
    }

    // The sheet

    @Test
    fun theModelChipOpensThePickerAndDismissingItClosesItWithoutAPick() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Postgres index", model = Gemini)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        assertFalse(viewModel.state.modelPickerOpen)

        viewModel.onOpenModelPicker()
        assertTrue(viewModel.state.modelPickerOpen)

        viewModel.onDismissModelPicker()
        runCurrent()
        assertFalse(viewModel.state.modelPickerOpen)
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
        assertTrue(repository.chooses.isEmpty())
    }

    @Test
    fun theSheetListsTheFavoritesAndPersonasTheRepositoryHolds() = runTest {
        val models = FakeModelRepository(favorites = listOf(Gpt5, Llama))
        val viewModel = chatViewModel(models = models)

        assertEquals(
            listOf("favorite:${Gpt5.id}", "favorite:${Llama.id}"),
            viewModel.state.modelPicker.favorites.map { it.key },
        )
        assertEquals(
            listOf("Code reviewer", "Terse", "Paper digest"),
            viewModel.state.modelPicker.personas.map { it.name },
        )

        models.favorites.value = emptyList()
        models.personas.value = listOf(Terse)
        runCurrent()
        assertTrue(viewModel.state.modelPicker.favorites.isEmpty())
        assertEquals(listOf("Terse"), viewModel.state.modelPicker.personas.map { it.name })
        assertEquals(ModelCatalogue.models.size, viewModel.state.modelPicker.allModels.size)
    }

    @Test
    fun openingTheSheetAsksFuelixForTheKeysModelsAndListsThemByVendor() = runTest {
        val haiku = ModelCatalogue["claude-haiku-4-5-20251001"]
        val qwen = ModelCatalogue["qwen3-coder"]
        val models = FakeModelRepository().apply { listed = listOf(Gpt5, haiku, qwen) }
        val viewModel = chatViewModel(models = models)
        assertEquals(ModelCatalogue.models, viewModel.state.models)
        assertEquals(0, models.refreshes)

        viewModel.onOpenModelPicker()
        // The sheet opens at once on the models it knows.
        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(ModelCatalogue.models.size, viewModel.state.modelPicker.allModels.size)

        runCurrent()
        assertEquals(1, models.refreshes)
        assertEquals(listOf("anthropic", "openai", "other"), viewModel.state.modelPicker.vendors.map { it.label })
        assertEquals(listOf(haiku, Gpt5, qwen), viewModel.state.modelPicker.allModels.map { it.choice.model })

        // Every opening asks again; dismissing does not.
        viewModel.onDismissModelPicker()
        runCurrent()
        assertEquals(1, models.refreshes)
        viewModel.onOpenModelPicker()
        runCurrent()
        assertEquals(2, models.refreshes)
    }

    @Test
    fun whenFuelixListsNothingOrTheRefreshFailsTheSheetKeepsTheModelsItKnows() = runTest {
        val models = FakeModelRepository(models = listOf(Gemini, Gpt5))
        val viewModel = chatViewModel(models = models)

        viewModel.onOpenModelPicker()
        runCurrent()
        assertEquals(1, models.refreshes)
        assertEquals(listOf(Gpt5, Gemini), viewModel.state.modelPicker.allModels.map { it.choice.model })

        models.refreshFailure = IllegalStateException("no connection")
        viewModel.onDismissModelPicker()
        viewModel.onOpenModelPicker()
        runCurrent()
        assertEquals(2, models.refreshes)
        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(listOf(Gpt5, Gemini), viewModel.state.modelPicker.allModels.map { it.choice.model })
    }

    @Test
    fun theSheetFollowsTheModelsTheRepositoryListsWheneverTheyChange() = runTest {
        val models = FakeModelRepository()
        val viewModel = chatViewModel(models = models)

        models.models.value = listOf(Mistral, Llama)
        runCurrent()

        assertEquals(listOf(Mistral, Llama), viewModel.state.models)
        assertEquals(listOf("meta", "mistral"), viewModel.state.modelPicker.vendors.map { it.label })
        assertEquals(0, models.refreshes)
    }

    // A new chat

    @Test
    fun aNewChatStartsOnTheDefaultModelAndFollowsItUntilSomethingIsPicked() = runTest {
        val models = FakeModelRepository(defaultModel = Gemini)
        val viewModel = chatViewModel(models = models)
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
        assertEquals("Gemini 2.5 Pro", viewModel.state.chipLabel)

        models.defaultModel.value = Llama
        runCurrent()
        assertEquals(ModelChoice(Llama), viewModel.state.choice)

        viewModel.onPick(ModelChoice(Mistral))
        models.defaultModel.value = Gpt5
        runCurrent()
        assertEquals(ModelChoice(Mistral), viewModel.state.choice)
    }

    @Test
    fun aPickInANewChatIsKeptAsItsChoiceClosesTheSheetAndWritesNothing() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository)
        viewModel.onOpenModelPicker()

        viewModel.onPick(ModelChoice(Reviewer))
        runCurrent()

        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)
        assertEquals(Reviewer.model, viewModel.state.model)
        assertEquals("Code reviewer · sonnet-4.5", viewModel.state.chipLabel)
        assertFalse(viewModel.state.modelPickerOpen)
        assertTrue(viewModel.state.showsEmptyState)
        assertTrue(repository.chooses.isEmpty())
        assertTrue(repository.sends.isEmpty())
        assertTrue(repository.conversations.value.isEmpty())
    }

    @Test
    fun theFirstSendOfANewChatCarriesItsChoice() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository, models = FakeModelRepository(defaultModel = Gemini))
        viewModel.onPick(ModelChoice(Terse))

        viewModel.send(Question)
        runCurrent()

        assertEquals(listOf<ModelChoice?>(ModelChoice(Terse)), repository.newChatChoices)
        val conversation = repository.conversations.value.single()
        assertEquals(Gpt5, conversation.model)
        assertEquals(Terse, conversation.persona)
        assertEquals(ModelChoice(Terse), viewModel.state.choice)
        assertEquals("gpt-5", viewModel.reply.modelShortName)
        assertEquals("Terse", viewModel.reply.personaName)

        // A send in the conversation it created hands no new-chat choice: it answers with its own.
        advance(ReplyTime)
        viewModel.send("And shorter?")
        runCurrent()
        assertEquals(listOf(ModelChoice(Terse), null), repository.newChatChoices)
    }

    @Test
    fun aFirstSendWithNothingPickedCarriesTheDefaultModel() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val viewModel = chatViewModel(repository, models = FakeModelRepository(defaultModel = Gemini))

        viewModel.send(Question)
        runCurrent()

        assertEquals(listOf<ModelChoice?>(ModelChoice(Gemini)), repository.newChatChoices)
        assertEquals(Gemini, repository.conversations.value.single().model)
        assertNull(viewModel.reply.personaName)
    }

    @Test
    fun aConversationGoneWhileOpenGoesBackToTheDefaultModelDroppingItsPersona() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Review this diff", persona = Reviewer)
        val models = FakeModelRepository(defaultModel = Gemini)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id), models)
        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)

        repository.delete(conversation.id)
        runCurrent()

        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
        assertEquals("Gemini 2.5 Pro", viewModel.state.chipLabel)
    }

    // An open conversation

    @Test
    fun aPickInAnOpenConversationChoosesItAndTheAccentFollowsAtOnce() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Postgres index")
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        viewModel.onOpenModelPicker()

        viewModel.onPick(ModelChoice(Gemini))
        // Before the database answers.
        assertEquals(Gemini, viewModel.state.model)
        assertEquals(ChatPalette.GEMINI, viewModel.state.model.palette)
        assertFalse(viewModel.state.modelPickerOpen)

        runCurrent()
        assertEquals(listOf(conversation.id to ModelChoice(Gemini)), repository.chooses)
        assertEquals(Gemini, repository.conversations.value.single().model)
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
        assertTrue(repository.sends.isEmpty())
    }

    @Test
    fun aPersonaPickedInAnOpenConversationAnswersOnItsModelAndAModelPickDropsIt() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Postgres index")
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))

        viewModel.onPick(ModelChoice(Terse))
        runCurrent()
        assertEquals(ModelChoice(Terse), viewModel.state.choice)
        assertEquals(Gpt5, viewModel.state.model)
        assertEquals("Terse · gpt-5", viewModel.state.chipLabel)
        assertEquals(Terse, repository.conversations.value.single().persona)

        viewModel.onPick(ModelChoice(Llama))
        runCurrent()
        assertEquals(ModelChoice(Llama), viewModel.state.choice)
        assertNull(viewModel.state.persona)
        assertEquals("Llama 4 Maverick", viewModel.state.chipLabel)
        assertNull(repository.conversations.value.single().persona)
        assertEquals(
            listOf(conversation.id to ModelChoice(Terse), conversation.id to ModelChoice(Llama)),
            repository.chooses,
        )
    }

    @Test
    fun aPickTheRepositoryRefusesShowsTheConversationsOwnChoiceAgain() = runTest {
        val repository = FakeChatRepository(backgroundScope).apply { chooseAnswer = { _, _ -> false } }
        val conversation = repository.seed("Postgres index", model = Gemini)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))

        viewModel.onPick(ModelChoice(Llama))
        assertEquals(Llama, viewModel.state.model)
        runCurrent()

        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
        assertEquals(Gemini, repository.conversations.value.single().model)
    }

    @Test
    fun aStreamingTickBeforeThePickIsSavedDoesNotTakeTheChipBack() = runTest {
        val saved = CompletableDeferred<Boolean>()
        val repository = FakeChatRepository(backgroundScope).apply { chooseAnswer = { _, _ -> saved.await() } }
        val conversation = repository.seed("Postgres index")
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))
        viewModel.send(Question)
        advance(ThinkingTime)

        viewModel.onPick(ModelChoice(Gemini))
        advance(TickTime * 2)
        assertEquals(Gemini, viewModel.state.model)
        // The reply on its way keeps the model it started with.
        assertEquals("sonnet-4.5", viewModel.reply.modelShortName)

        saved.complete(true)
        runCurrent()
        assertEquals(Gemini, repository.conversations.value.single().model)
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
        advance(ReplyTime)
        assertEquals("sonnet-4.5", viewModel.reply.modelShortName)

        // The next reply comes from the pick.
        viewModel.send("And a GIN index?")
        runCurrent()
        assertEquals("gemini-2.5-pro", viewModel.reply.modelShortName)
    }

    @Test
    fun anOpenConversationKeepsItsOwnModelWhenTheDefaultChanges() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val models = FakeModelRepository()
        val conversation = repository.seed("Postgres index", model = Gemini)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id), models)

        models.defaultModel.value = Llama
        runCurrent()

        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
    }

    @Test
    fun aRecentRowWithAPersonaShowsItOnTheChipFromTheTap() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Review this diff", persona = Reviewer)
        val viewModel = chatViewModel(repository, models = FakeModelRepository(defaultModel = Gemini))

        viewModel.onOpenRecent(conversation.id)

        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)
        assertEquals("Code reviewer · sonnet-4.5", viewModel.state.chipLabel)
    }

    @Test
    fun eachReplyShowsTheModelAndPersonaItWasWrittenWith() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Review this diff", persona = Reviewer)
        val viewModel = chatViewModel(repository, ChatEntry(conversation.id))

        viewModel.onPick(ModelChoice(Llama))
        viewModel.send("Now as a summary")
        advance(ReplyTime)

        assertEquals(
            listOf("sonnet-4.5" to "Code reviewer", "llama-4" to null),
            viewModel.replies.map { it.modelShortName to it.personaName },
        )
    }

    // Favorites and the mark

    @Test
    fun theStarTogglesAFavoriteAndKeepsTheSheetOpenWithoutPicking() = runTest {
        val models = FakeModelRepository()
        val viewModel = chatViewModel(models = models)
        viewModel.onOpenModelPicker()

        viewModel.onToggleFavorite(Gemini)
        runCurrent()
        viewModel.onToggleFavorite(Llama)
        runCurrent()

        assertEquals(listOf(Gemini to false, Llama to true), models.favoriteWrites)
        assertEquals(
            listOf("Claude Sonnet 4.5", "GPT-5", "Llama 4 Maverick"),
            viewModel.state.modelPicker.favorites.map { it.name },
        )
        val stars = viewModel.state.modelPicker.allModels.associate { it.choice.model.id to it.favorite }
        assertEquals(false, stars.getValue(Gemini.id))
        assertEquals(true, stars.getValue(Llama.id))
        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(ModelChoice(ModelCatalogue.default), viewModel.state.choice)
    }

    @Test
    fun theActiveMarkIsOnWhatAnswersTheChat() = runTest {
        val repository = FakeChatRepository(backgroundScope)
        val conversation = repository.seed("Postgres index", model = Gemini)
        val viewModel = chatViewModel(repository)
        assertEquals(pickerRowKeys(ModelCatalogue.default), viewModel.state.modelPicker.marked)

        viewModel.onPick(ModelChoice(Reviewer))
        assertEquals(listOf("persona:reviewer"), viewModel.state.modelPicker.marked)

        viewModel.onPick(ModelChoice(Llama))
        assertEquals(listOf("model:${Llama.id}"), viewModel.state.modelPicker.marked)

        viewModel.onOpenRecent(conversation.id)
        runCurrent()
        assertEquals(pickerRowKeys(Gemini), viewModel.state.modelPicker.marked)
    }
}
