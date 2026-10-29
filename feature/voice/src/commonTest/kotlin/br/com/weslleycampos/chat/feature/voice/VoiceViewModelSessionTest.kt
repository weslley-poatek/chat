package br.com.weslleycampos.chat.feature.voice

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.core.navigation.entries.settings.SettingsEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.feature.chat.palette
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerMark
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerVariant
import br.com.weslleycampos.chat.feature.voice.resources.FeatureVoiceRes
import br.com.weslleycampos.chat.feature.voice.resources.voice_mute
import br.com.weslleycampos.chat.feature.voice.resources.voice_unmute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val Sonnet = ModelCatalogue.default

/** What the user says first, four words. */
private const val Question = "so the hook works"

/** What the user says next. */
private const val FollowUp = "what about unmount"

/** Voice's session on `VoiceViewModel`: the model chip and its sheet, mute, "end", "Aa", History and Settings. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelSessionTest {
    private val started = StartedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.voiceViewModel(
        voices: FakeVoiceRepository = FakeVoiceRepository(),
        models: FakeModelRepository = FakeModelRepository(),
        input: FakeSpeechInput = FakeSpeechInput(),
        navigator: NavigatorImpl = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) }),
        entry: VoiceEntry = navigator.navBackStack.last() as VoiceEntry,
    ): VoiceViewModel {
        val viewModel = VoiceViewModel(
            entry = entry,
            navigator = navigator,
            voices = voices,
            models = models,
            languages = FakeVoiceLanguageRepository(),
            input = input,
            output = FakeSpeechOutput(),
        )
        runCurrent()
        started.add(viewModel).onStart()
        runCurrent()
        return viewModel
    }

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    private val VoiceViewModel.state: VoiceUiState get() = uiState.value

    private fun VoiceViewModel.pick(choice: ModelChoice) {
        picker.onOpen()
        picker.onPick(choice)
    }

    /** A speech input on which the user says [lines], one a listen, and then nothing. */
    private fun saying(vararg lines: String) = FakeSpeechInput().apply { lines.forEach { says(it) } }

    // The model chip and its sheet

    @Test
    fun theChipOpensTheChatSheetOnWhatAnswersAndScrimOrBackClosesIt() = started.runTest {
        val viewModel = voiceViewModel()
        assertEquals("sonnet-4.5", viewModel.state.chipLabel)

        viewModel.picker.onOpen()
        assertTrue(viewModel.state.modelPickerOpen)
        val sheet = viewModel.state.modelPicker
        assertEquals(ModelPickerVariant.Chat, sheet.variant)
        assertFalse(sheet.compareOn)
        assertNull(sheet.compareHint)
        assertEquals(3, sheet.personas.size)
        assertEquals(
            listOf("favorite:${Sonnet.id}", "model:${Sonnet.id}"),
            (sheet.favorites + sheet.personas + sheet.allModels)
                .filter { it.mark == ModelPickerMark.Active }
                .map { it.key },
        )

        viewModel.picker.onDismiss()
        assertFalse(viewModel.state.modelPickerOpen)
    }

    @Test
    fun aPickBeforeTheFirstTranscriptIsKeptForItAndMakesItsModelTheDefault() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices, input = saying(Question, FollowUp))

        viewModel.pick(ModelChoice(Reviewer))
        assertFalse(viewModel.state.modelPickerOpen)
        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)
        runCurrent()
        assertEquals(listOf<Pair<Long?, ModelChoice>>(null to ModelChoice(Reviewer)), voices.choices)
        // The session reads the default again, which holds no persona: the chip keeps the pick.
        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)

        advance(spokenTime(Question))
        assertEquals(
            listOf(SavedTranscript(conversationId = null, text = Question, newSession = ModelChoice(Reviewer))),
            voices.transcripts,
        )
        assertEquals(ModelChoice(Reviewer), voices.conversations.value.getValue(1L).choice)
        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)

        // The conversation holds the pick from then on.
        advance(AnswerTime + spokenTime(Replies[0]) + spokenTime(FollowUp))
        assertEquals(SavedTranscript(conversationId = 1, text = FollowUp), voices.transcripts.last())
    }

    @Test
    fun aModelPickedBeforeTheFirstTranscriptTurnsTheAccentAtOnce() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices, input = saying(Question))

        viewModel.pick(ModelChoice(Gpt5))
        assertEquals("gpt-5", viewModel.state.chipLabel)
        assertEquals(ChatPalette.GPT5, viewModel.state.model.palette)
        runCurrent()
        assertEquals(Gpt5, voices.defaultModel.value)
        assertEquals(ModelChoice(Gpt5), viewModel.state.choice)

        advance(spokenTime(Question))
        assertEquals(
            listOf(SavedTranscript(conversationId = null, text = Question, newSession = ModelChoice(Gpt5))),
            voices.transcripts,
        )
    }

    @Test
    fun aPickAfterTheFirstTranscriptChoosesForTheConversation() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices, input = saying(Question, FollowUp))
        advance(spokenTime(Question) + 500.milliseconds)
        assertEquals(1L, viewModel.state.conversationId)

        viewModel.pick(ModelChoice(Gemini))
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
        runCurrent()
        assertEquals(listOf<Pair<Long?, ModelChoice>>(1L to ModelChoice(Gemini)), voices.choices)
        assertEquals(ModelChoice(Gemini), voices.conversations.value.getValue(1L).choice)
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)

        // The conversation's next transcript passes no new-session choice: 500 ms into thinking at the pick.
        advance(AnswerTime - 500.milliseconds + spokenTime(Replies[0]) + spokenTime(FollowUp))
        assertEquals(
            listOf(
                SavedTranscript(conversationId = null, text = Question),
                SavedTranscript(conversationId = 1, text = FollowUp),
            ),
            voices.transcripts,
        )
    }

    @Test
    fun aPickThatChangesNothingPutsTheChipBackOnWhatAnswers() = started.runTest {
        val voices = FakeVoiceRepository().apply { refuseChoices = true }
        val viewModel = voiceViewModel(voices = voices, input = saying(Question))

        viewModel.pick(ModelChoice(Reviewer))
        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)
        runCurrent()
        assertEquals(ModelChoice(Sonnet), viewModel.state.choice)

        // The refused persona is not kept for the first transcript either.
        advance(spokenTime(Question))
        assertEquals(listOf(SavedTranscript(conversationId = null, text = Question)), voices.transcripts)

        viewModel.pick(ModelChoice(Gemini))
        runCurrent()
        assertEquals(ModelChoice(Sonnet), viewModel.state.choice)
    }

    @Test
    fun aStarTogglesAFavoriteWithoutPickingIt() = started.runTest {
        val models = FakeModelRepository()
        val viewModel = voiceViewModel(models = models)
        viewModel.picker.onOpen()

        viewModel.picker.onToggleFavorite(Gemini)
        runCurrent()
        assertEquals(listOf(Gemini to false), models.favoriteWrites)
        assertEquals(listOf(Sonnet.id, Gpt5.id), viewModel.state.favorites.map { it.id })
        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(ModelChoice(Sonnet), viewModel.state.choice)
    }

    @Test
    fun theChipAsksFuelixForTheKeysModelsAndTheSheetListsThemByVendor() = started.runTest {
        val haiku = ModelCatalogue["claude-haiku-4-5-20251001"]
        val qwen = ModelCatalogue["qwen3-coder"]
        val models = FakeModelRepository().apply { listed = listOf(qwen, Gpt5, haiku) }
        val viewModel = voiceViewModel(models = models)
        assertEquals(ModelCatalogue.models, viewModel.state.models)
        assertEquals(0, models.refreshes)

        viewModel.picker.onOpen()
        // The sheet opens at once on the models it knows.
        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(ModelCatalogue.models.size, viewModel.state.modelPicker.allModels.size)

        runCurrent()
        assertEquals(1, models.refreshes)
        assertEquals(listOf(qwen, Gpt5, haiku), viewModel.state.models)
        assertEquals(listOf("anthropic", "openai", "other"), viewModel.state.modelPicker.vendors.map { it.label })

        // Every opening asks again; dismissing does not.
        viewModel.picker.onDismiss()
        runCurrent()
        assertEquals(1, models.refreshes)
        viewModel.picker.onOpen()
        runCurrent()
        assertEquals(2, models.refreshes)
    }

    @Test
    fun whenFuelixListsNothingOrTheRefreshFailsTheSheetKeepsTheModelsItKnows() = started.runTest {
        val models = FakeModelRepository(models = listOf(Gemini, Gpt5))
        val viewModel = voiceViewModel(models = models, input = saying(Question))

        viewModel.picker.onOpen()
        runCurrent()
        assertEquals(1, models.refreshes)
        assertEquals(listOf(Gemini, Gpt5), viewModel.state.models)

        models.refreshFailure = IllegalStateException("no connection")
        viewModel.picker.onDismiss()
        viewModel.picker.onOpen()
        runCurrent()
        assertEquals(2, models.refreshes)
        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(listOf(Gpt5, Gemini), viewModel.state.modelPicker.allModels.map { it.choice.model })
        // The phases go on meanwhile.
        advance(spokenTime(Question))
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
    }

    // Mute

    @Test
    fun muteTurnsTheMicrophoneOffAndItsLabelSaysWhatATapDoes() = started.runTest {
        val input = FakeSpeechInput()
        val viewModel = voiceViewModel(input = input)
        assertEquals(FeatureVoiceRes.string.voice_mute, viewModel.state.muteLabel)
        assertTrue(input.listening)

        viewModel.onToggleMute()
        runCurrent()
        assertTrue(viewModel.state.muted)
        assertEquals(FeatureVoiceRes.string.voice_unmute, viewModel.state.muteLabel)
        assertFalse(input.listening)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)

        viewModel.onToggleMute()
        runCurrent()
        assertFalse(viewModel.state.muted)
        assertEquals(FeatureVoiceRes.string.voice_mute, viewModel.state.muteLabel)
        assertTrue(input.listening)
    }

    // "end"

    @Test
    fun endWhileListeningSavesNothingAndListeningStartsAgain() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply { trailsOff("so the") }
        val viewModel = voiceViewModel(voices = voices, input = input)
        advance(SpokenWord * 2)
        input.says(FollowUp)

        viewModel.onEnd()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(0, viewModel.state.words)
        assertEquals(VoiceCaption.Empty, viewModel.state.caption)
        assertNull(viewModel.state.conversationId)
        assertEquals(1, input.cancellations)
        assertEquals(2, input.listens)
        assertEquals(emptyList(), voices.transcripts)

        advance(spokenTime(FollowUp))
        assertEquals(listOf(SavedTranscript(conversationId = null, text = FollowUp)), voices.transcripts)
    }

    @Test
    fun endClosesTheSessionAndAFreshOneStartsANewConversation() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices, input = saying(Question, FollowUp))
        viewModel.pick(ModelChoice(Gemini))
        advance(spokenTime(Question) + 500.milliseconds)
        assertEquals(1L, viewModel.state.conversationId)

        // Ended while thinking: the transcript stays, with no reply.
        viewModel.onEnd()
        runCurrent()
        assertNull(viewModel.state.conversationId)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertNull(viewModel.state.exchange)
        // A fresh session is on the default model, which the pick made Gemini.
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)

        advance(spokenTime(FollowUp) + AnswerTime)
        assertEquals(
            listOf(
                SavedTranscript(conversationId = null, text = Question, newSession = ModelChoice(Gemini)),
                SavedTranscript(conversationId = null, text = FollowUp),
            ),
            voices.transcripts,
        )
        assertEquals(listOf(2L), voices.replies.map { it.conversationId })
        assertEquals(2L, viewModel.state.conversationId)
    }

    @Test
    fun endLeavesTheEntrysConversationForANewSessionAndDropsAPersonaPickedForIt() = started.runTest {
        val voices = FakeVoiceRepository(conversations = mapOf(5L to FakeVoiceConversation(ModelChoice(Gemini))))
        val input = FakeSpeechInput()
        val viewModel = voiceViewModel(
            voices = voices,
            input = input,
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry(conversationId = 5)) }),
        )
        assertEquals(5L, viewModel.state.conversationId)

        viewModel.onEnd()
        runCurrent()
        assertNull(viewModel.state.conversationId)
        assertEquals(ModelChoice(Sonnet), viewModel.state.choice)

        viewModel.pick(ModelChoice(Reviewer))
        runCurrent()
        input.says(Question)
        viewModel.onEnd()
        runCurrent()
        assertEquals(ModelChoice(Sonnet), viewModel.state.choice)
        advance(spokenTime(Question))
        assertEquals(listOf(SavedTranscript(conversationId = null, text = Question)), voices.transcripts)
    }

    @Test
    fun endWhileStoppedDoesNotListen() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput()
        val viewModel = voiceViewModel(voices = voices, input = input)
        viewModel.onStop()

        viewModel.onEnd()
        advance(60_000.milliseconds)

        assertEquals(1, input.listens)
        assertFalse(input.listening)
        assertEquals(emptyList(), voices.transcripts)
    }

    // "Aa", History and Settings

    @Test
    fun textStopsThePhasesAndOpensTheSessionAsTextOverVoice() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = saying(Question)
        val navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) })
        val viewModel = voiceViewModel(voices = voices, input = input, navigator = navigator)
        advance(spokenTime(Question) + 500.milliseconds)

        viewModel.onOpenText()
        assertEquals(listOf(VoiceEntry(), ChatEntry(conversationId = 1)), navigator.navBackStack.toList())
        advance(60_000.milliseconds)
        assertEquals(1, voices.transcripts.size)
        assertEquals(emptyList(), voices.replies)

        // Back returns to Voice, which listens again, on the conversation.
        assertTrue(navigator.navigateUp())
        assertEquals(listOf<NavKey>(VoiceEntry()), navigator.navBackStack.toList())
        input.says(FollowUp)
        viewModel.onStart()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(VoiceCaption.Empty, viewModel.state.caption)
        advance(spokenTime(FollowUp))
        assertEquals(SavedTranscript(conversationId = 1, text = FollowUp), voices.transcripts.last())
    }

    @Test
    fun textBeforeTheFirstTranscriptOpensANewChat() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply { trailsOff("so the") }
        val navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) })
        val viewModel = voiceViewModel(voices = voices, input = input, navigator = navigator)
        advance(SpokenWord * 2)

        viewModel.onOpenText()
        assertEquals(listOf(VoiceEntry(), ChatEntry()), navigator.navBackStack.toList())
        advance(60_000.milliseconds)
        assertFalse(input.listening)
        assertEquals(emptyList(), voices.transcripts)
    }

    @Test
    fun historyStopsThePhasesAndOpensOverVoiceWhichBackReturnsTo() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply { trailsOff("so the") }
        val navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) })
        val viewModel = voiceViewModel(voices = voices, input = input, navigator = navigator)
        advance(SpokenWord * 2)

        viewModel.onOpenHistory()
        assertEquals(listOf(VoiceEntry(), HistoryEntry), navigator.navBackStack.toList())
        advance(60_000.milliseconds)
        assertFalse(input.listening)
        assertEquals(emptyList(), voices.transcripts)

        assertTrue(navigator.navigateUp())
        assertEquals(listOf<NavKey>(VoiceEntry()), navigator.navBackStack.toList())
        // Voice is the root: back from it leaves the app.
        assertFalse(navigator.navigateUp())
    }

    @Test
    fun settingsStopsThePhasesAndOpensOverVoiceWhichBackReturnsTo() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply { trailsOff("so the") }
        val navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) })
        val viewModel = voiceViewModel(voices = voices, input = input, navigator = navigator)
        advance(SpokenWord * 2)

        viewModel.onOpenSettings()
        assertEquals(listOf(VoiceEntry(), SettingsEntry), navigator.navBackStack.toList())
        advance(60_000.milliseconds)
        assertFalse(input.listening)
        assertEquals(emptyList(), voices.transcripts)

        // Settings' back is `navigateUp()`: Voice again, which listens again once resumed.
        assertTrue(navigator.navigateUp())
        assertEquals(listOf<NavKey>(VoiceEntry()), navigator.navBackStack.toList())
        input.says(Question)
        viewModel.onStart()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        advance(spokenTime(Question))
        assertEquals(listOf(SavedTranscript(conversationId = null, text = Question)), voices.transcripts)
    }
}
