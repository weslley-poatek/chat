package br.com.weslleycampos.chat.feature.voice

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.models.VoiceAnswer
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import br.com.weslleycampos.chat.feature.voice.speech.Heard
import br.com.weslleycampos.chat.feature.voice.speech.SpeechProblem
import br.com.weslleycampos.chat.feature.voice.speech.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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

private val Portuguese = VoiceLanguage.PORTUGUESE_BRAZIL
private val English = VoiceLanguage.ENGLISH_US

/**
 * Listening on `VoiceViewModel`, on the fakes' speech: the words as the device hears them, what ends
 * listening (the device, silence, a tap), hearing nothing, mute, a device that cannot listen, and the
 * microphone's permission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelListeningTest {
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
        languages: FakeVoiceLanguageRepository = FakeVoiceLanguageRepository(),
        start: Boolean = true,
    ): VoiceViewModel {
        val viewModel = VoiceViewModel(
            entry = entry,
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { add(entry) }),
            voices = voices,
            models = FakeModelRepository(),
            languages = languages,
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

    // Listening

    @Test
    fun listeningShowsEachWordAsItIsHeardAndEndsWhenTheDeviceSaysTheUserStopped() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply { says(Question) }
        val viewModel = voiceViewModel(voices = voices, input = input)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(VoiceCaption.Empty, viewModel.state.caption)
        assertTrue(input.listening)

        advance(SpokenWord)
        assertEquals(VoiceCaption(shown = "so", rest = ""), viewModel.state.caption)
        assertEquals(1, viewModel.state.words)
        advance(SpokenWord * 2)
        assertEquals(VoiceCaption(shown = "so the hook", rest = ""), viewModel.state.caption)
        assertEquals(3, viewModel.state.words)
        advance(SpokenWord - 1.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(emptyList(), voices.transcripts)

        advance(1.milliseconds)
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(VoiceCaption(shown = Question, rest = ""), viewModel.state.caption)
        assertEquals(listOf(SavedTranscript(conversationId = null, text = Question)), voices.transcripts)
        assertFalse(input.listening)
        assertEquals(1, input.listens)
    }

    @Test
    fun onceSomethingWasHeardTwoSecondsWithNoNewWordEndListening() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply { trailsOff(Question) }
        val viewModel = voiceViewModel(voices = voices, input = input)

        advance(spokenTime(Question) + SilenceAfterSpeech - 1.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(emptyList(), voices.transcripts)
        advance(1.milliseconds)

        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(listOf(SavedTranscript(conversationId = null, text = Question)), voices.transcripts)
        // The device never said the user stopped: the microphone is turned off for it.
        assertFalse(input.listening)
        assertEquals(1, input.cancellations)
        assertEquals(2000.milliseconds, SilenceAfterSpeech)
    }

    @Test
    fun eachNewWordStartsTheSilenceOver() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply {
            queue {
                emit(Heard.Partial("so"))
                delay(1900.milliseconds)
                emit(Heard.Partial("so the"))
                awaitCancellation()
            }
        }
        val viewModel = voiceViewModel(voices = voices, input = input)

        advance(SilenceAfterSpeech)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(VoiceCaption(shown = "so the", rest = ""), viewModel.state.caption)
        advance(1900.milliseconds - 1.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        advance(1.milliseconds)

        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(listOf("so the"), voices.transcripts.map { it.text })
    }

    @Test
    fun untilAWordIsHeardListeningWaitsHoweverLong() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply {
            queue {
                emit(Heard.Partial(""))
                awaitCancellation()
            }
        }
        val viewModel = voiceViewModel(voices = voices, input = input)

        advance(60_000.milliseconds)

        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(VoiceCaption.Empty, viewModel.state.caption)
        assertTrue(input.listening)
        assertEquals(1, input.listens)
        assertEquals(emptyList(), voices.transcripts)
    }

    @Test
    fun aTapEndsListeningWithTheWordsHeardSoFar() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply { trailsOff(Question) }
        val viewModel = voiceViewModel(voices = voices, input = input)
        advance(SpokenWord * 2)

        tap(viewModel)

        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(VoiceCaption(shown = "so the", rest = ""), viewModel.state.caption)
        assertEquals(listOf(SavedTranscript(conversationId = null, text = "so the")), voices.transcripts)
        assertFalse(input.listening)
        assertEquals(1, input.cancellations)
    }

    @Test
    fun hearingNothingListensAgainAfterAPause() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply {
            hearsNothing()
            says(Question)
        }
        val viewModel = voiceViewModel(voices = voices, input = input)

        advance(SpokenWord)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertFalse(input.listening)
        advance(ListenAgainPause - 1.milliseconds)
        assertEquals(1, input.listens)
        advance(1.milliseconds)
        assertEquals(2, input.listens)
        assertTrue(input.listening)
        assertEquals(emptyList(), voices.transcripts)

        advance(spokenTime(Question))
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(listOf(Question), voices.transcripts.map { it.text })
        assertEquals(500.milliseconds, ListenAgainPause)
    }

    @Test
    fun aListenThatHearsNothingOrATapBeforeAnyWordListensAgain() = started.runTest {
        val voices = FakeVoiceRepository()
        // The first listen ends at once with nothing; the second waits for a word that never comes.
        val input = FakeSpeechInput().apply { queue {} }
        val viewModel = voiceViewModel(voices = voices, input = input)
        assertEquals(1, input.listens)

        advance(ListenAgainPause)
        assertEquals(2, input.listens)
        tap(viewModel)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertFalse(input.listening)
        advance(ListenAgainPause)

        assertEquals(3, input.listens)
        assertTrue(input.listening)
        assertEquals(emptyList(), voices.transcripts)
    }

    // The language

    @Test
    fun eachListenAndEachReplySpokenIsInTheLanguageSettingsPickedAsItStarts() = started.runTest {
        val languages = FakeVoiceLanguageRepository(VoiceLanguages(Portuguese))
        val input = FakeSpeechInput().apply { says(Question) }
        val output = FakeSpeechOutput()
        voiceViewModel(input = input, output = output, languages = languages)
        advance(toSpeaking)
        // An English reply is still said in the one language picked.
        assertEquals(listOf(Replies[0]), output.texts)

        // Picked while the reply is spoken: the next listen is in the new language.
        languages.languages.value = VoiceLanguages(English)
        advance(spokenTime(Replies[0]))
        assertTrue(input.listening)
        assertEquals(listOf(listOf(Portuguese), listOf(English)), input.languages)
        assertEquals(listOf(Portuguese), output.languages)
    }

    @Test
    fun bilingualEachListenIsForBothAndEachReplyIsSaidInTheLanguageItIsWrittenIn() = started.runTest {
        val portugueseReply = "use uma flag de leading edge no hook"
        val englishReply = "add a leading flag to the hook"
        val replies = ArrayDeque(listOf(portugueseReply, englishReply))
        val voices = FakeVoiceRepository().apply { answerScript = { VoiceAnswer.Spoken(replies.removeFirst()) } }
        val input = FakeSpeechInput().apply {
            says(Question)
            says(FollowUp)
        }
        val output = FakeSpeechOutput()
        val languages = FakeVoiceLanguageRepository(VoiceLanguages(Portuguese, bilingual = true))
        voiceViewModel(voices = voices, input = input, output = output, languages = languages)

        advance(spokenTime(Question))
        advance(spokenTime(portugueseReply))
        advance(spokenTime(FollowUp))

        assertEquals(listOf(portugueseReply, englishReply), output.texts)
        assertEquals(listOf(Portuguese, English), output.languages)
        assertEquals(listOf(listOf(Portuguese, English), listOf(Portuguese, English)), input.languages)
    }

    // Mute

    @Test
    fun muteTurnsTheMicrophoneOffMidListenAndUnmutingListensAfresh() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply {
            trailsOff("so the")
            says(FollowUp)
        }
        val viewModel = voiceViewModel(voices = voices, input = input)
        advance(SpokenWord * 2)

        viewModel.onToggleMute()
        runCurrent()
        assertTrue(viewModel.state.muted)
        assertFalse(input.listening)
        assertEquals(1, input.cancellations)
        advance(60_000.milliseconds)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(1, input.listens)
        assertEquals(emptyList(), voices.transcripts)

        viewModel.onToggleMute()
        runCurrent()
        assertEquals(2, input.listens)
        assertTrue(input.listening)
        advance(spokenTime(FollowUp))
        // What was heard before muting is dropped, not added to.
        assertEquals(listOf(SavedTranscript(conversationId = null, text = FollowUp)), voices.transcripts)
    }

    @Test
    fun unmutingStartsTheCaptionFromNothing() = started.runTest {
        val input = FakeSpeechInput().apply { trailsOff("so the") }
        val viewModel = voiceViewModel(input = input)
        advance(SpokenWord * 2)
        viewModel.onToggleMute()
        runCurrent()

        viewModel.onToggleMute()
        runCurrent()

        // Listening again, nothing heard yet: the words heard before muting are not shown as if kept.
        assertTrue(input.listening)
        assertEquals(0, viewModel.state.words)
        assertEquals(VoiceCaption.Empty, viewModel.state.caption)
    }

    @Test
    fun whileMutedSpeakingGoesOnAndTheNextListeningWaitsForUnmute() = started.runTest {
        val input = FakeSpeechInput().apply { says(Question) }
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(input = input, output = output)
        advance(toSpeaking)
        assertEquals(ChatVoicePhase.Speaking, viewModel.state.phase)

        viewModel.onToggleMute()
        advance(spokenTime(Replies[0]))
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(0, output.cancellations)
        advance(60_000.milliseconds)
        assertEquals(1, input.listens)
        assertFalse(input.listening)

        viewModel.onToggleMute()
        runCurrent()
        assertEquals(2, input.listens)
        assertTrue(input.listening)
    }

    // A device that cannot listen

    @Test
    fun aDeviceThatCannotListenShowsWhyFor1400MsAndStopsUntilATap() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply {
            fails(SpeechProblem.NOT_ALLOWED)
            says(Question)
        }
        val viewModel = voiceViewModel(voices = voices, input = input)
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(SpeechProblem.NOT_ALLOWED.toast, viewModel.state.toast)
        assertFalse(input.listening)

        advance(ChatToastDefaults.Duration - 1.milliseconds)
        assertEquals(SpeechProblem.NOT_ALLOWED.toast, viewModel.state.toast)
        advance(1.milliseconds)
        assertNull(viewModel.state.toast)
        // It does not try again on its own.
        advance(60_000.milliseconds)
        assertEquals(1, input.listens)

        tap(viewModel)
        assertEquals(2, input.listens)
        advance(spokenTime(Question))
        assertEquals(ChatVoicePhase.Thinking, viewModel.state.phase)
        assertEquals(listOf(Question), voices.transcripts.map { it.text })
    }

    @Test
    fun aListenThatFailsLaterShowsWhyAndStopsThere() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply {
            says(Question)
            fails(SpeechProblem.FAILED)
        }
        val viewModel = voiceViewModel(voices = voices, input = input)

        advance(toSpeaking + spokenTime(Replies[0]))

        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(SpeechProblem.FAILED.toast, viewModel.state.toast)
        assertEquals(VoiceCaption.Empty, viewModel.state.caption)
        advance(60_000.milliseconds)
        assertEquals(2, input.listens)
        assertEquals(1, voices.transcripts.size)
    }

    @Test
    fun afterASpeechProblemTheScreenComingBackListensAgainButATapWhileItIsAwayDoesNot() = started.runTest {
        val input = FakeSpeechInput().apply { fails(SpeechProblem.UNAVAILABLE) }
        val viewModel = voiceViewModel(input = input)
        assertEquals(SpeechProblem.UNAVAILABLE.toast, viewModel.state.toast)

        viewModel.onStop()
        tap(viewModel)
        assertEquals(1, input.listens)

        viewModel.onStart()
        runCurrent()
        assertEquals(2, input.listens)
        assertTrue(input.listening)
    }

    // The microphone

    @Test
    fun withoutTheMicrophoneNothingListensAndAllowingItStartsAtOnce() = started.runTest {
        val input = FakeSpeechInput()
        val viewModel = voiceViewModel(input = input, start = false)
        viewModel.onMicrophone(false)
        assertFalse(viewModel.state.microphone)

        viewModel.onStart()
        runCurrent()
        assertEquals(0, input.listens)
        tap(viewModel)
        advance(60_000.milliseconds)
        assertEquals(0, input.listens)

        viewModel.onMicrophone(true)
        runCurrent()
        assertTrue(viewModel.state.microphone)
        assertEquals(1, input.listens)
        assertTrue(input.listening)
    }

    @Test
    fun theMicrophoneAllowedWhileTheScreenIsAwayWaitsForItToResume() = started.runTest {
        val input = FakeSpeechInput()
        val viewModel = voiceViewModel(input = input, start = false)
        assertTrue(viewModel.state.microphone)

        viewModel.onMicrophone(true)
        runCurrent()
        assertEquals(0, input.listens)
        viewModel.onStart()
        runCurrent()
        assertEquals(1, input.listens)

        viewModel.onStop()
        viewModel.onMicrophone(true)
        runCurrent()
        assertEquals(1, input.listens)
        assertFalse(input.listening)
    }

    @Test
    fun losingTheMicrophoneStopsSpeakingAndGettingItBackListensAfresh() = started.runTest {
        val input = FakeSpeechInput().apply { says(Question) }
        val output = FakeSpeechOutput()
        val viewModel = voiceViewModel(input = input, output = output)
        advance(toSpeaking + SpokenWord)

        viewModel.onMicrophone(false)
        runCurrent()
        assertFalse(output.speaking)
        assertEquals(1, output.cancellations)
        advance(60_000.milliseconds)
        assertEquals(1, input.listens)

        viewModel.onMicrophone(true)
        runCurrent()
        assertEquals(ChatVoicePhase.Listening, viewModel.state.phase)
        assertEquals(2, input.listens)
        assertTrue(input.listening)
    }

    @Test
    fun losingTheMicrophoneMidListenTurnsItOffAndSavesNothing() = started.runTest {
        val voices = FakeVoiceRepository()
        val input = FakeSpeechInput().apply { trailsOff(Question) }
        val viewModel = voiceViewModel(voices = voices, input = input)
        advance(SpokenWord * 2)

        viewModel.onMicrophone(false)
        advance(60_000.milliseconds)

        assertFalse(input.listening)
        assertEquals(1, input.cancellations)
        assertEquals(emptyList(), voices.transcripts)
    }
}
