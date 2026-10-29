package br.com.weslleycampos.chat.feature.voice

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.chat.models.VoiceAnswer
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import br.com.weslleycampos.chat.feature.chat.toast
import br.com.weslleycampos.chat.feature.voice.speech.SpeechProblem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

/** What the user says first, four words: heard whole [SpokenWord] × 4 in. */
private const val Question = "so the hook works"

/** What the user says next. */
private const val FollowUp = "what about unmount"

/**
 * Voice's phase machine on `VoiceViewModel`, on the fakes' speech, once something was heard: thinking
 * for as long as Fuelix takes, speaking for as long as the device says the reply, a reply Fuelix could
 * not give, and the screen leaving. Listening is `VoiceViewModelListeningTest`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {
    private val started = StartedViewModels()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The view model on its fakes, resumed unless [start] is `false`. */
    private fun TestScope.voiceViewModel(
        voices: FakeVoiceRepository = FakeVoiceRepository(),
        input: FakeSpeechInput = FakeSpeechInput(),
        output: FakeSpeechOutput = FakeSpeechOutput(),
        entry: VoiceEntry = VoiceEntry(),
        start: Boolean = true,
    ): VoiceViewModel {
        val viewModel = VoiceViewModel(
            entry = entry,
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(entry) }),
            voices = voices,
            models = FakeModelRepository(),
            input = input,
            output = output,
        )
        runCurrent()
        started.add(viewModel)
        if (start) viewModel.onStart()
        runCurrent()
        return viewModel
    }

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    private fun TestScope.tap(viewModel: VoiceViewModel) {
        viewModel.onSphereTap()
        runCurrent()
    }

    private val VoiceViewModel.state: VoiceUiState get() = uiState.value

    /** From the start to the first reply being spoken: [Question] heard, then Fuelix's time. */
    private val toSpeaking: Duration = spokenTime(Question) + AnswerTime

    // Thinking

    @Test
    fun theTranscriptIsSavedAsListeningEndsAndThinkingLastsUntilTheReplyArrives() = started.runTest {
        val voices = FakeVoiceRepository()
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(
            voices = voices,
            input = FakeSpeechInput().apply { says(Question) },
            output = output,
        )

        advance(spokenTime(Question))
        val turn = VoiceTurn(conversationId = 1, transcriptId = 100, exchange = VoiceExchange(transcript = Question))
        assertEquals(listOf(turn), voices.asked)
        assertEquals(1L, viewModel.state.conversationId)
        advance(AnswerTime - 1.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(emptyList(), voices.replies)
        assertEquals(emptyList(), output.texts)

        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        assertEquals(listOf(turn), voices.replies)
        assertEquals(VoiceExchange(transcript = Question, reply = Replies[0]), viewModel.state.exchange)
    }

    @Test
    fun thinkingWaitsForTheReplyHoweverLongItTakes() = started.runTest {
        val reply = CompletableDeferred<VoiceAnswer>()
        val voices = FakeVoiceRepository().apply { answerScript = { reply.await() } }
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(
            voices = voices,
            input = FakeSpeechInput().apply { says(Question) },
            output = output,
        )
        advance(spokenTime(Question))

        advance(60_000.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(VoiceCaption(shown = Question, rest = ""), viewModel.state.caption)

        reply.complete(VoiceAnswer.Spoken("ok then"))
        runCurrent()
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        assertEquals(listOf("ok then"), output.texts)
    }

    @Test
    fun tapsWhileThinkingAreIgnoredAndNotCarriedIntoSpeaking() = started.runTest {
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(input = FakeSpeechInput().apply { says(Question) }, output = output)
        advance(spokenTime(Question) + 1000.milliseconds)

        tap(viewModel)
        tap(viewModel)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)

        advance(AnswerTime - 1000.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        advance(spokenTime(Replies[0]) - 1.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        assertEquals(0, output.cancellations)
        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
    }

    @Test
    fun aTranscriptThatIsNotSavedAsksForNoReplyAndListensAgain() = started.runTest {
        val voices = FakeVoiceRepository().apply { refuseTranscripts = true }
        val input = FakeSpeechInput().apply { says(Question) }
        val viewModel = voiceViewModel(voices = voices, input = input)

        advance(spokenTime(Question))

        assertEquals(listOf(Question), voices.transcripts.map { it.text })
        assertEquals(emptyList(), voices.asked)
        assertNull(viewModel.state.conversationId)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(VoiceCaption.Empty, viewModel.state.caption)
        assertEquals(2, input.listens)
    }

    @Test
    fun aReplyThatIsDroppedListensAgain() = started.runTest {
        val voices = FakeVoiceRepository().apply {
            answerScript = {
                delay(1000.milliseconds)
                VoiceAnswer.Dropped
            }
        }
        val input = FakeSpeechInput().apply { says(Question) }
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(voices = voices, input = input, output = output)
        advance(spokenTime(Question))

        advance(1000.milliseconds - 1.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        advance(1.milliseconds)

        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(VoiceCaption.Empty, viewModel.state.caption)
        assertEquals(2, input.listens)
        assertEquals(emptyList(), output.texts)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun theFirstTranscriptMovesTheSessionToTheConversationItCreatedAndTheNextOneSavesThere() = started.runTest {
        val voices = FakeVoiceRepository()
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(
            voices = voices,
            input = FakeSpeechInput().apply {
                says(Question)
                says(FollowUp)
            },
            output = output,
        )
        assertNull(viewModel.state.conversationId)

        advance(spokenTime(Question))
        assertEquals(1L, viewModel.state.conversationId)
        advance(AnswerTime + spokenTime(Replies[0]) + spokenTime(FollowUp))
        assertEquals(
            listOf(
                SavedTranscript(conversationId = null, text = Question),
                SavedTranscript(conversationId = 1, text = FollowUp),
            ),
            voices.transcripts,
        )
        advance(AnswerTime)
        assertEquals(VoiceExchange(transcript = FollowUp, reply = Replies[1]), viewModel.state.exchange)
        assertEquals(listOf(Replies[0], Replies[1]), output.texts)
        assertEquals(1, voices.conversations.value.size)
    }

    @Test
    fun anEntrysConversationSavesItsTranscriptsThereAndOneThatIsGoneStartsANewSession() = started.runTest {
        val voices = FakeVoiceRepository(conversations = mapOf(5L to FakeVoiceConversation(ModelChoice(Gemini))))
        val onFive = voiceViewModel(
            voices = voices,
            input = FakeSpeechInput().apply { says(Question) },
            entry = VoiceEntry(conversationId = 5),
        )
        assertEquals(ModelChoice(Gemini), onFive.state.choice)
        advance(spokenTime(Question))
        assertEquals(listOf(SavedTranscript(conversationId = 5, text = Question)), voices.transcripts)
        onFive.onStop()

        val gone = voiceViewModel(
            voices = voices,
            input = FakeSpeechInput().apply { says(FollowUp) },
            entry = VoiceEntry(conversationId = 9),
        )
        assertNull(gone.state.conversationId)
        advance(spokenTime(FollowUp))
        assertEquals(SavedTranscript(conversationId = null, text = FollowUp), voices.transcripts.last())
        assertEquals(6L, gone.state.conversationId)
    }

    // Speaking

    @Test
    fun speakingSaysTheReplyShowingEachWordAsItStartsAndListensAgainWhenItEnds() = started.runTest {
        val input = FakeSpeechInput().apply { says(Question) }
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(input = input, output = output)

        advance(toSpeaking)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        assertEquals(listOf(Replies[0]), output.texts)
        assertTrue(output.speaking)
        assertEquals(VoiceCaption(shown = "add", rest = " a leading flag"), viewModel.state.caption)
        advance(SpokenWord)
        assertEquals(VoiceCaption(shown = "add a", rest = " leading flag"), viewModel.state.caption)
        advance(SpokenWord * 2)
        assertEquals(VoiceCaption(shown = Replies[0], rest = ""), viewModel.state.caption)

        // The last word is still being said.
        advance(SpokenWord - 1.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertFalse(output.speaking)
        assertEquals(0, output.cancellations)
        assertEquals(2, input.listens)
    }

    @Test
    fun aTapStopsSpeakingAtOnceAndListensAgain() = started.runTest {
        val input = FakeSpeechInput().apply { says(Question) }
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(input = input, output = output)
        advance(toSpeaking + SpokenWord)

        tap(viewModel)

        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(0, viewModel.state.words)
        assertFalse(output.speaking)
        assertEquals(1, output.cancellations)
        assertEquals(2, input.listens)
    }

    @Test
    fun aDeviceThatCannotSpeakShowsAWordEvery230MsForTheRepliesSpeakingDuration() = started.runTest {
        val output = FakeSpeechOutput().apply { failure = SpeechProblem.UNAVAILABLE }
        val viewModel = voiceViewModel(input = FakeSpeechInput().apply { says(Question) }, output = output)

        advance(toSpeaking)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        assertEquals(listOf(Replies[0]), output.texts)
        assertEquals(0, viewModel.state.words)
        advance(WordInterval)
        assertEquals(1, viewModel.state.words)
        advance(WordInterval * 3)
        assertEquals(VoiceCaption(shown = Replies[0], rest = ""), viewModel.state.caption)

        advance(speakingDuration(Replies[0]) - WordInterval * 4 - 1.milliseconds)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        // Silent speaking is no failure: nothing to say about it.
        assertNull(viewModel.state.toast)
    }

    @Test
    fun aTapEndsTheWordsOfADeviceThatCannotSpeak() = started.runTest {
        val input = FakeSpeechInput().apply { says(Question) }
        val output = FakeSpeechOutput().apply { failure = SpeechProblem.FAILED }
        val viewModel = voiceViewModel(input = input, output = output)
        advance(toSpeaking + WordInterval)

        tap(viewModel)

        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(2, input.listens)
    }

    // A reply Fuelix could not give

    @Test
    fun aFailedReplyShowsWhyFor1400MsAndStopsAtListeningWithNothingHeard() = started.runTest {
        val voices = FakeVoiceRepository().apply { answerFailure = ReplyFailure.NO_KEY }
        val input = FakeSpeechInput().apply { says(Question) }
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(voices = voices, input = input, output = output)

        advance(toSpeaking - 1.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertNull(viewModel.state.toast)
        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(VoiceCaption.Empty, viewModel.state.caption)
        assertEquals(ReplyFailure.NO_KEY.toast, viewModel.state.toast)
        // The transcript stays, with no reply, and nothing is said.
        assertEquals(1L, viewModel.state.conversationId)
        assertEquals(emptyList(), voices.replies)
        assertEquals(emptyList(), output.texts)

        advance(ChatToastDefaults.Duration - 1.milliseconds)
        assertEquals(ReplyFailure.NO_KEY.toast, viewModel.state.toast)
        advance(1.milliseconds)
        assertNull(viewModel.state.toast)
        // The microphone stays off, however long it waits.
        advance(60_000.milliseconds)
        assertEquals(1, input.listens)
        assertFalse(input.listening)
    }

    @Test
    fun aTapAfterAFailedReplyListensAgain() = started.runTest {
        val voices = FakeVoiceRepository().apply { answerFailure = ReplyFailure.UNREACHABLE }
        val input = FakeSpeechInput().apply { says(Question) }
        val viewModel = voiceViewModel(voices = voices, input = input)
        advance(toSpeaking)
        voices.answerFailure = null
        input.says(FollowUp)

        tap(viewModel)
        assertEquals(2, input.listens)
        advance(spokenTime(FollowUp) + AnswerTime)

        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)
        assertEquals(listOf(null, 1L), voices.transcripts.map { it.conversationId })
        assertEquals(VoiceExchange(transcript = FollowUp, reply = Replies[0]), viewModel.state.exchange)
    }

    @Test
    fun aReplyThatThrowsIsAFailedReply() = started.runTest {
        val voices = FakeVoiceRepository().apply { answerScript = { error("socket closed") } }
        val viewModel = voiceViewModel(voices = voices, input = FakeSpeechInput().apply { says(Question) })

        advance(spokenTime(Question))

        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(ReplyFailure.FAILED.toast, viewModel.state.toast)
    }

    @Test
    fun endAfterAFailureStartsAFreshSessionAtOnce() = started.runTest {
        val voices = FakeVoiceRepository().apply { answerFailure = ReplyFailure.NO_KEY }
        val input = FakeSpeechInput().apply { says(Question) }
        val viewModel = voiceViewModel(voices = voices, input = input)
        advance(toSpeaking)
        voices.answerFailure = null
        input.says(FollowUp)

        viewModel.onEnd()
        runCurrent()
        assertNull(viewModel.state.conversationId)
        assertEquals(2, input.listens)
        advance(spokenTime(FollowUp))

        assertEquals(
            listOf(
                SavedTranscript(conversationId = null, text = Question),
                SavedTranscript(conversationId = null, text = FollowUp),
            ),
            voices.transcripts,
        )
        assertEquals(2L, viewModel.state.conversationId)
    }

    @Test
    fun afterAFailedReplyTheScreenComingBackListensAgainButATapWhileItIsAwayDoesNot() = started.runTest {
        val voices = FakeVoiceRepository().apply { answerFailure = ReplyFailure.RATE_LIMITED }
        val input = FakeSpeechInput().apply { says(Question) }
        val viewModel = voiceViewModel(voices = voices, input = input)
        advance(toSpeaking)

        viewModel.onStop()
        tap(viewModel)
        assertEquals(1, input.listens)

        viewModel.onStart()
        runCurrent()
        assertEquals(2, input.listens)
        assertTrue(input.listening)
    }

    // Starting and stopping

    @Test
    fun stoppingTurnsTheMicrophoneOffAndStartingAgainListensAfresh() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply { trailsOff(Question) }
        val viewModel = voiceViewModel(voices = voices, input = input)
        advance(SpokenWord * 2)

        viewModel.onStop()
        advance(60_000.milliseconds)
        assertFalse(input.listening)
        assertEquals(1, input.cancellations)
        assertEquals(emptyList(), voices.transcripts)

        viewModel.onStart()
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(VoiceCaption.Empty, viewModel.state.caption)
        assertEquals(2, input.listens)
    }

    @Test
    fun stoppingWhileThinkingKeepsTheTranscriptAndAsksForTheReplyNoLonger() = started.runTest {
        val voices = FakeVoiceRepository()
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(
            voices = voices,
            input = FakeSpeechInput().apply { says(Question) },
            output = output,
        )
        advance(spokenTime(Question) + 1000.milliseconds)

        viewModel.onStop()
        advance(60_000.milliseconds)

        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(1, voices.transcripts.size)
        assertEquals(1, voices.asked.size)
        assertEquals(emptyList(), voices.replies)
        assertEquals(emptyList(), output.texts)
    }

    @Test
    fun stoppingWhileSpeakingStopsTheVoice() = started.runTest {
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(input = FakeSpeechInput().apply { says(Question) }, output = output)
        advance(toSpeaking + SpokenWord)

        viewModel.onStop()
        runCurrent()

        assertFalse(output.speaking)
        assertEquals(1, output.cancellations)
    }

    @Test
    fun startingWhilePlayingChangesNothingAndATapWhileStoppedIsForgotten() = started.runTest {
        val input = FakeSpeechInput().apply { trailsOff(Question) }
        val viewModel = voiceViewModel(input = input)
        advance(SpokenWord * 2)

        viewModel.onStart()
        runCurrent()
        assertEquals(1, input.listens)
        assertEquals(VoiceCaption(shown = "so the", rest = ""), viewModel.state.caption)

        viewModel.onStop()
        tap(viewModel)
        assertEquals(1, input.listens)
        viewModel.onStart()
        runCurrent()
        assertEquals(2, input.listens)
    }
}
