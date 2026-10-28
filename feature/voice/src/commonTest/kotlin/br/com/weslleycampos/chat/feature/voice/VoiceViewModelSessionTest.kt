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
        navigator: NavigatorImpl = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) }),
        entry: VoiceEntry = navigator.navBackStack.last() as VoiceEntry,
    ): VoiceViewModel {
        val viewModel = VoiceViewModel(entry = entry, navigator = navigator, voices = voices, models = models)
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
            listOf("favorite:sonnet", "model:sonnet"),
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
        val viewModel = voiceViewModel(voices = voices)

        viewModel.pick(ModelChoice(Reviewer))
        assertFalse(viewModel.state.modelPickerOpen)
        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)
        runCurrent()
        assertEquals(listOf<Pair<Long?, ModelChoice>>(null to ModelChoice(Reviewer)), voices.choices)
        // The session reads the default again, which holds no persona: the chip keeps the pick.
        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)

        advance(6500.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to ModelChoice(Reviewer)), voices.transcripts)
        assertEquals(ModelChoice(Reviewer), voices.conversations.value.getValue(1L).choice)
        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)

        // The conversation holds the pick from then on.
        advance(15_900.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to ModelChoice(Reviewer), 1L to null), voices.transcripts)
    }

    @Test
    fun aModelPickedBeforeTheFirstTranscriptTurnsTheAccentAtOnce() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)

        viewModel.pick(ModelChoice(Gpt5))
        assertEquals("gpt-5", viewModel.state.chipLabel)
        assertEquals(ChatPalette.GPT5, viewModel.state.model.palette)
        runCurrent()
        assertEquals(Gpt5, voices.defaultModel.value)
        assertEquals(ModelChoice(Gpt5), viewModel.state.choice)

        advance(6500.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to ModelChoice(Gpt5)), voices.transcripts)
    }

    @Test
    fun aPickAfterTheFirstTranscriptChoosesForTheConversation() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)
        advance(7000.milliseconds)
        assertEquals(1L, viewModel.state.conversationId)

        viewModel.pick(ModelChoice(Gemini))
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
        runCurrent()
        assertEquals(listOf<Pair<Long?, ModelChoice>>(1L to ModelChoice(Gemini)), voices.choices)
        assertEquals(ModelChoice(Gemini), voices.conversations.value.getValue(1L).choice)
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)

        // The conversation's next transcript passes no new-session choice.
        advance(15_400.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null, 1L to null), voices.transcripts)
    }

    @Test
    fun aPickThatChangesNothingPutsTheChipBackOnWhatAnswers() = started.runTest {
        val voices = FakeVoiceRepository().apply { refuseChoices = true }
        val viewModel = voiceViewModel(voices = voices)

        viewModel.pick(ModelChoice(Reviewer))
        assertEquals(ModelChoice(Reviewer), viewModel.state.choice)
        runCurrent()
        assertEquals(ModelChoice(Sonnet), viewModel.state.choice)

        // The refused persona is not kept for the first transcript either.
        advance(6500.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null), voices.transcripts)

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
        assertEquals(listOf("sonnet", "gpt5"), viewModel.state.favorites.map { it.id })
        assertTrue(viewModel.state.modelPickerOpen)
        assertEquals(ModelChoice(Sonnet), viewModel.state.choice)
    }

    // Mute

    @Test
    fun muteOnlyStillsTheListeningSphereAndThePhasesAndTranscriptGoOn() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)
        assertEquals(FeatureVoiceRes.string.voice_mute, viewModel.state.muteLabel)

        viewModel.onToggleMute()
        assertTrue(viewModel.state.muted)
        assertEquals(FeatureVoiceRes.string.voice_unmute, viewModel.state.muteLabel)

        advance(460.milliseconds)
        assertEquals(2, viewModel.state.words)
        advance(6040.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(1, voices.transcripts.size)
        advance(2400.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        assertEquals(1, voices.replies.size)
        assertTrue(viewModel.state.muted)

        viewModel.onToggleMute()
        assertFalse(viewModel.state.muted)
        assertEquals(FeatureVoiceRes.string.voice_mute, viewModel.state.muteLabel)
    }

    // "end"

    @Test
    fun endWhileListeningSavesNothingAndListeningStartsAgain() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)
        advance(4000.milliseconds)

        viewModel.onEnd()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(0, viewModel.state.words)
        assertNull(viewModel.state.conversationId)
        advance(6499.milliseconds)
        assertEquals(emptyList(), voices.transcripts)
        advance(1.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null), voices.transcripts)
    }

    @Test
    fun endClosesTheSessionAndAFreshOneStartsANewConversation() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)
        viewModel.pick(ModelChoice(Gemini))
        advance(7000.milliseconds)
        assertEquals(1L, viewModel.state.conversationId)

        // Ended while thinking: the transcript stays, with no reply.
        viewModel.onEnd()
        runCurrent()
        assertNull(viewModel.state.conversationId)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(Exchanges[0], viewModel.state.exchange)
        // A fresh session is on the default model, which the pick made Gemini.
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)

        advance(8900.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to ModelChoice(Gemini), null to null), voices.transcripts)
        assertEquals(listOf(2L), voices.replies.map { it.conversationId })
        assertEquals(2L, viewModel.state.conversationId)
    }

    @Test
    fun endLeavesTheEntrysConversationForANewSessionAndDropsAPersonaPickedForIt() = started.runTest {
        val voices = FakeVoiceRepository(conversations = mapOf(5L to FakeVoiceConversation(ModelChoice(Gemini))))
        val viewModel = voiceViewModel(
            voices = voices,
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry(conversationId = 5)) }),
        )
        assertEquals(5L, viewModel.state.conversationId)

        viewModel.onEnd()
        runCurrent()
        assertNull(viewModel.state.conversationId)
        assertEquals(ModelChoice(Sonnet), viewModel.state.choice)

        viewModel.pick(ModelChoice(Reviewer))
        runCurrent()
        viewModel.onEnd()
        runCurrent()
        assertEquals(ModelChoice(Sonnet), viewModel.state.choice)
        advance(6500.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null), voices.transcripts)
    }

    @Test
    fun endWhileStoppedStartsNoTimer() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)
        viewModel.onStop()

        viewModel.onEnd()
        advance(60_000.milliseconds)
        assertEquals(emptyList(), voices.transcripts)
    }

    // "Aa", History and Settings

    @Test
    fun textStopsThePhasesAndOpensTheSessionAsTextOverVoice() = started.runTest {
        val voices = FakeVoiceRepository()
        val navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) })
        val viewModel = voiceViewModel(voices = voices, navigator = navigator)
        advance(7000.milliseconds)

        viewModel.onOpenText()
        assertEquals(listOf(VoiceEntry(), ChatEntry(conversationId = 1)), navigator.navBackStack.toList())
        advance(60_000.milliseconds)
        assertEquals(1, voices.transcripts.size)
        assertEquals(emptyList(), voices.replies)

        // Back returns to Voice, which listens again on the conversation's next exchange.
        assertTrue(navigator.navigateUp())
        assertEquals(listOf<NavKey>(VoiceEntry()), navigator.navBackStack.toList())
        viewModel.onStart()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(Exchanges[1], viewModel.state.exchange)
        advance(6500.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null, 1L to null), voices.transcripts)
    }

    @Test
    fun textBeforeTheFirstTranscriptOpensANewChat() = started.runTest {
        val voices = FakeVoiceRepository()
        val navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) })
        val viewModel = voiceViewModel(voices = voices, navigator = navigator)
        advance(3000.milliseconds)

        viewModel.onOpenText()
        assertEquals(listOf(VoiceEntry(), ChatEntry()), navigator.navBackStack.toList())
        advance(60_000.milliseconds)
        assertEquals(emptyList(), voices.transcripts)
    }

    @Test
    fun historyStopsThePhasesAndOpensOverVoiceWhichBackReturnsTo() = started.runTest {
        val voices = FakeVoiceRepository()
        val navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) })
        val viewModel = voiceViewModel(voices = voices, navigator = navigator)
        advance(3000.milliseconds)

        viewModel.onOpenHistory()
        assertEquals(listOf(VoiceEntry(), HistoryEntry), navigator.navBackStack.toList())
        advance(60_000.milliseconds)
        assertEquals(emptyList(), voices.transcripts)

        assertTrue(navigator.navigateUp())
        assertEquals(listOf<NavKey>(VoiceEntry()), navigator.navBackStack.toList())
        // Voice is the root: back from it leaves the app.
        assertFalse(navigator.navigateUp())
    }

    @Test
    fun settingsStopsThePhasesAndOpensOverVoiceWhichBackReturnsTo() = started.runTest {
        val voices = FakeVoiceRepository()
        val navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(VoiceEntry()) })
        val viewModel = voiceViewModel(voices = voices, navigator = navigator)
        advance(3000.milliseconds)

        viewModel.onOpenSettings()
        assertEquals(listOf(VoiceEntry(), SettingsEntry), navigator.navBackStack.toList())
        advance(60_000.milliseconds)
        assertEquals(emptyList(), voices.transcripts)

        // Settings' back is `navigateUp()`: Voice again, which listens again once resumed.
        assertTrue(navigator.navigateUp())
        assertEquals(listOf<NavKey>(VoiceEntry()), navigator.navBackStack.toList())
        viewModel.onStart()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        advance(6500.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null), voices.transcripts)
    }
}
