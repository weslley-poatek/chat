package br.com.weslleycampos.chat.feature.voice

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
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
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Voice's phase machine on `VoiceViewModel`: the phases and their times, the words, taps, and what
 * each phase saves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {
    private val started = StartedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.voiceViewModel(
        voices: FakeVoiceRepository = FakeVoiceRepository(),
        entry: VoiceEntry = VoiceEntry(),
    ): VoiceViewModel {
        val viewModel = VoiceViewModel(
            entry = entry,
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(entry) }),
            voices = voices,
            models = FakeModelRepository(),
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

    @Test
    fun listeningLasts6500MsThinking2400MsAndSpeaking7000MsThenListeningAgainForever() = started.runTest {
        val viewModel = voiceViewModel()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)

        advance(6499.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)

        advance(2399.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)

        advance(6999.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)

        // And round again, with the same times.
        advance(6500.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        advance(2400.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        advance(7000.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
    }

    @Test
    fun aWordShowsEvery230MsAndEachPhaseStartsAgainFromNone() = started.runTest {
        val viewModel = voiceViewModel()
        assertEquals(0, viewModel.state.words)
        assertEquals(VoiceCaption(shown = "", rest = "so the hook works"), viewModel.state.caption)

        advance(229.milliseconds)
        assertEquals(0, viewModel.state.words)
        advance(1.milliseconds)
        assertEquals(1, viewModel.state.words)
        advance(230.milliseconds)
        assertEquals(2, viewModel.state.words)
        assertEquals(VoiceCaption(shown = "so the", rest = " hook works"), viewModel.state.caption)

        // 28 words by 6440 ms, then thinking starts at 6500 with none.
        advance(5980.milliseconds)
        assertEquals(28, viewModel.state.words)
        advance(60.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(0, viewModel.state.words)
        advance(230.milliseconds)
        assertEquals(1, viewModel.state.words)

        // Speaking reveals the reply from its first word.
        advance(2170.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        assertEquals(VoiceCaption(shown = "", rest = "add a leading flag"), viewModel.state.caption)
        advance(690.milliseconds)
        assertEquals(VoiceCaption(shown = "add a leading", rest = " flag"), viewModel.state.caption)
    }

    @Test
    fun aTapEndsThePhaseAtOnceAndTheNextOneGetsItsWholeTime() = started.runTest {
        val viewModel = voiceViewModel()
        advance(1000.milliseconds)

        viewModel.onSphereTap()
        runCurrent()
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(0, viewModel.state.words)

        advance(2399.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)

        advance(460.milliseconds)
        assertEquals(2, viewModel.state.words)
        viewModel.onSphereTap()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(0, viewModel.state.words)
        advance(6499.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
    }

    @Test
    fun theTranscriptIsSavedWhenListeningEndsAndTheReplyWhenThinkingEnds() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)

        advance(6499.milliseconds)
        assertEquals(emptyList(), voices.transcripts)
        advance(1.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null), voices.transcripts)
        assertEquals(emptyList(), voices.replies)

        advance(2399.milliseconds)
        assertEquals(emptyList(), voices.replies)
        advance(1.milliseconds)
        assertEquals(listOf(VoiceTurn(conversationId = 1, transcriptId = 100, exchange = Exchanges[0])), voices.replies)

        // Speaking saves nothing more.
        advance(6999.milliseconds)
        assertEquals(1, voices.transcripts.size)
        assertEquals(1, voices.replies.size)
    }

    @Test
    fun aTapThatEndsListeningOrThinkingSavesWhatTheTimerWould() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)
        advance(800.milliseconds)

        viewModel.onSphereTap()
        runCurrent()
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null), voices.transcripts)
        assertEquals(emptyList(), voices.replies)

        advance(300.milliseconds)
        viewModel.onSphereTap()
        runCurrent()
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        assertEquals(listOf(VoiceTurn(conversationId = 1, transcriptId = 100, exchange = Exchanges[0])), voices.replies)

        // Skipping speaking saves nothing: the reply is already whole.
        viewModel.onSphereTap()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(1, voices.transcripts.size)
        assertEquals(1, voices.replies.size)
    }

    @Test
    fun theFirstTranscriptMovesTheSessionToTheConversationItCreated() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)
        assertNull(viewModel.state.conversationId)

        advance(6500.milliseconds)
        assertEquals(1L, viewModel.state.conversationId)

        // The next loop plays the conversation's next exchange and saves into it.
        advance(9400.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(Exchanges[1], viewModel.state.exchange)
        advance(6500.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null, 1L to null), voices.transcripts)
        advance(2400.milliseconds)
        assertEquals(listOf(1L, 1L), voices.replies.map { it.conversationId })
        assertEquals(Exchanges[1], voices.replies.last().exchange)
        assertEquals(1, voices.conversations.value.size)
    }

    @Test
    fun listeningTakesTheSessionsNextExchangeWhenItStartsAndThinkingAndSpeakingPlayTheSavedTurns() = started.runTest {
        val voices = FakeVoiceRepository(
            conversations = mapOf(5L to FakeVoiceConversation(ModelChoice(Gemini), userMessages = 1)),
        )
        val viewModel = voiceViewModel(voices = voices, entry = VoiceEntry(conversationId = 5))
        assertEquals(5L, viewModel.state.conversationId)
        assertEquals(ModelChoice(Gemini), viewModel.state.choice)
        assertEquals(Exchanges[1], viewModel.state.exchange)

        // A typed message meanwhile moves the session's next on; listening keeps the line it started.
        voices.conversations.update { it + (5L to it.getValue(5L).copy(userMessages = 2)) }
        advance(3000.milliseconds)
        assertEquals(Exchanges[1], viewModel.state.exchange)

        // The transcript saved was the next one, so thinking and speaking play the turn's.
        advance(3500.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(5L to null), voices.transcripts)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(Exchanges[2], viewModel.state.exchange)
        assertEquals(VoiceCaption(shown = Exchanges[2].transcript, rest = ""), viewModel.state.caption)
        advance(2400.milliseconds)
        assertEquals(Exchanges[2], viewModel.state.exchange)
        assertEquals(Exchanges[2], voices.replies.single().exchange)
    }

    @Test
    fun aTranscriptThatIsNotSavedSavesNoReplyAndTheLoopGoesOn() = started.runTest {
        val voices = FakeVoiceRepository().apply { refuseTranscripts = true }
        val viewModel = voiceViewModel(voices = voices)

        advance(6500.milliseconds)
        assertEquals(1, voices.transcripts.size)
        assertNull(viewModel.state.conversationId)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(VoiceCaption(shown = Exchanges[0].transcript, rest = ""), viewModel.state.caption)

        advance(2400.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        assertEquals(Exchanges[0], viewModel.state.exchange)
        assertEquals(emptyList(), voices.replies)

        // Once a transcript can be saved again, the next loop saves it, offering the same exchange.
        voices.refuseTranscripts = false
        advance(7000.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(Exchanges[0], viewModel.state.exchange)
        advance(8900.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null, null to null), voices.transcripts)
        assertEquals(1L, viewModel.state.conversationId)
        assertEquals(1, voices.replies.size)
    }

    @Test
    fun aConversationThatIsGoneStartsOverAsANewSession() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices, entry = VoiceEntry(conversationId = 9))
        assertNull(viewModel.state.conversationId)
        assertEquals(Exchanges[0], viewModel.state.exchange)

        advance(6500.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null), voices.transcripts)
        assertEquals(1L, viewModel.state.conversationId)
    }

    @Test
    fun stoppingHoldsThePhasesAndStartingAgainBeginsAtListening() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)
        advance(7000.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)

        viewModel.onStop()
        advance(60_000.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(1, voices.transcripts.size)
        // Left while thinking: the transcript stays and no reply is saved.
        assertEquals(emptyList(), voices.replies)

        viewModel.onStart()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(0, viewModel.state.words)
        assertEquals(Exchanges[1], viewModel.state.exchange)
        advance(6500.milliseconds)
        assertEquals(listOf<Pair<Long?, ModelChoice?>>(null to null, 1L to null), voices.transcripts)
    }

    @Test
    fun startingWhilePlayingChangesNothingAndATapWhileStoppedIsForgotten() = started.runTest {
        val voices = FakeVoiceRepository()
        val viewModel = voiceViewModel(voices = voices)
        advance(3000.milliseconds)

        viewModel.onStart()
        runCurrent()
        assertEquals(13, viewModel.state.words)
        advance(3500.milliseconds)
        assertEquals(1, voices.transcripts.size)

        viewModel.onStop()
        viewModel.onSphereTap()
        runCurrent()
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        viewModel.onStart()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        advance(6499.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
    }
}
