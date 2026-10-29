package br.com.weslleycampos.chat.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.core.navigation.entries.settings.SettingsEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.VoiceRepository
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.chat.models.VoiceAnswer
import br.com.weslleycampos.chat.data.chat.models.VoiceSession
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import br.com.weslleycampos.chat.feature.chat.toast
import br.com.weslleycampos.chat.feature.voice.speech.SpeechInput
import br.com.weslleycampos.chat.feature.voice.speech.SpeechOutput
import br.com.weslleycampos.chat.feature.voice.speech.SpeechProblem
import br.com.weslleycampos.chat.feature.voice.speech.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

/**
 * The Voice mode frame's logic, the home screen: the prototype's phase machine (`startVoice` …
 * `stopVoice`, lines 737–753), mute and the model chip, on [VoiceRepository]. The phases run on
 * [VoicePlayer] while the screen is resumed ([onStart] / [onStop]) and the microphone is allowed
 * ([onMicrophone]): it listens with the device's speech recognition ([SpeechInput]), Fuelix answers,
 * and the device says the reply ([SpeechOutput]). Every exchange is saved as it is heard: the
 * transcript when listening ends, the reply when Fuelix gives it. A voice session is therefore an
 * ordinary conversation, listed in History and readable as text. When Fuelix gives no reply, or the
 * device cannot listen, a toast says why and the phases stop until a tap on the sphere.
 *
 * The session starts on the entry's conversation, or on a new one; its first transcript creates the
 * conversation and the session moves to it ([VoiceUiState.conversationId]). A conversation that is
 * gone starts over as a new session. "end" ([onEnd]) closes the session and starts a fresh one at
 * listening. The model chip's sheet is [picker].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel(binds = [VoiceViewModel::class])
class VoiceViewModel(
    @InjectedParam entry: VoiceEntry,
    val navigator: Navigator,
    private val voices: VoiceRepository,
    models: ModelRepository,
    input: SpeechInput,
    output: SpeechOutput,
) : ViewModel(), Navigator by navigator {
    val uiState: StateFlow<VoiceUiState>
        field = MutableStateFlow(VoiceUiState(conversationId = entry.conversationId))

    /** The session as last read for [VoiceUiState.conversationId]; `null` before the first read. */
    private val session = MutableStateFlow<VoiceSession?>(null)

    /** Moves on "end", so a transcript still being saved for the ended session moves nothing. */
    private var generation = 0

    /** The model chip's sheet. */
    val picker = VoicePicker(
        scope = viewModelScope,
        state = uiState,
        voices = voices,
        models = models,
        sessionChoice = { session.value?.takeIf { it.conversationId == uiState.value.conversationId }?.choice },
    )

    private val player = VoicePlayer(
        scope = viewModelScope,
        state = uiState,
        turns = object : VoiceTurns {
            override suspend fun saveTranscript(transcript: String): VoiceTurn? = saveTranscriptOnce(transcript)

            override suspend fun answer(turn: VoiceTurn): VoiceAnswer =
                attempt(fallback = VoiceAnswer.Failed(ReplyFailure.FAILED)) { voices.answer(turn) }

            override fun failed(failure: ReplyFailure) = showToast(failure.toast)

            override fun unheard(problem: SpeechProblem) = showToast(problem.toast)
        },
        input = input,
        output = output,
    )

    private var toastJob: Job? = null

    /** The screen is resumed: the phases play while the microphone is allowed. */
    private var resumed = false

    init {
        viewModelScope.launch {
            models.observeModels().collect { listed -> uiState.update { it.copy(models = listed) } }
        }
        viewModelScope.launch {
            models.observeFavorites().collect { favorites -> uiState.update { it.copy(favorites = favorites) } }
        }
        viewModelScope.launch {
            models.observePersonas().collect { personas -> uiState.update { it.copy(personas = personas) } }
        }
        viewModelScope.launch {
            uiState
                .map { it.conversationId }
                .distinctUntilChanged()
                .flatMapLatest { id -> voices.observeSession(id).map { id to it } }
                .collect { (id, observed) -> onSession(id, observed) }
        }
    }

    /**
     * The screen is resumed, the prototype's entering voice mode: the phases start at listening, once
     * the microphone is allowed.
     */
    fun onStart() {
        resumed = true
        if (uiState.value.microphone) player.start()
    }

    /**
     * The screen is paused or left, for History, the text, another app or a rotation, the
     * prototype's leaving voice mode: the microphone turns off and speaking stops. Nothing is saved
     * or undone: a phase cut short keeps what was saved before it.
     */
    fun onStop() {
        resumed = false
        player.stop()
    }

    /**
     * Whether the microphone is allowed, as the platform says on every resume and after its prompt:
     * the phases play only while it is.
     */
    fun onMicrophone(granted: Boolean) {
        uiState.update { it.copy(microphone = granted) }
        if (!granted) {
            player.stop()
        } else if (resumed) {
            player.start()
        }
    }

    /**
     * A tap on the sphere, the prototype's `voiceAdvance`: listening ends with what was heard, or
     * speaking stops; after a failure, the phases start again.
     */
    fun onSphereTap() = player.tap()

    /** Mute, the prototype's `toggleMute`: the microphone turns off, and listening waits until unmuted. */
    fun onToggleMute() = uiState.update { it.copy(muted = !it.muted) }

    /**
     * "end", the prototype's `exitVoice`: the session closes. What it saved stays, a conversation in
     * History; a fresh session starts at listening on the default model, whatever conversation the
     * entry opened.
     */
    fun onEnd() {
        // A player a failure stopped was waiting for a tap; the fresh session starts it as well.
        val running = player.running || player.halted
        player.stop()
        generation++
        picker.reset()
        // A new session's chip is the default model's; a conversation's waits for the new session's read.
        val fresh = session.value?.takeIf { it.conversationId == null }
        uiState.update {
            it.copy(
                conversationId = null,
                choice = fresh?.choice ?: it.choice,
                phase = ChatVoicePhase.Listening,
                words = 0,
            )
        }
        if (running) player.start()
    }

    /**
     * Saves [transcript] and, on the first one of a new session, moves the session to the
     * conversation it created. It runs to the end even when the screen leaves meanwhile, so the
     * session never loses the conversation its transcript went into.
     */
    private suspend fun saveTranscriptOnce(transcript: String): VoiceTurn? = withContext(NonCancellable) {
        val from = generation
        val conversationId = uiState.value.conversationId
        val newSession = picker.pending.takeIf { conversationId == null }
        val turn = attempt(fallback = null) { voices.saveTranscript(conversationId, transcript, newSession) }
        if (turn != null && from == generation && uiState.value.conversationId == conversationId) {
            if (conversationId == null) picker.onFirstTranscript()
            uiState.update { it.copy(conversationId = turn.conversationId) }
        }
        turn
    }

    private fun onSession(conversationId: Long?, observed: VoiceSession?) {
        val shown = uiState.value
        if (conversationId != shown.conversationId) return
        if (observed == null) {
            // Deleted, or never there: a new session, as after "end".
            uiState.update { it.copy(conversationId = null) }
            return
        }
        session.value = observed
        // A new session shows its own pick until its first transcript; a conversation, its choice.
        val pending = if (conversationId == null) picker.pending else null
        uiState.update { it.copy(choice = pending ?: observed.choice) }
    }

    /** [message] as a toast, for [ChatToastDefaults.Duration]; another one restarts the time. */
    private fun showToast(message: StringResource) {
        toastJob?.cancel()
        uiState.update { it.copy(toast = message) }
        toastJob = viewModelScope.launch {
            delay(ChatToastDefaults.Duration)
            uiState.update { it.copy(toast = null) }
        }
    }

    /** Runs [action]; the prototype has no failure to show, so a failure is [fallback] and the loop goes on. */
    private suspend fun <T> attempt(fallback: T, action: suspend () -> T): T = try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (ignored: Exception) {
        fallback
    }
}

/**
 * The History button, where the prototype's back chevron was: the phases stop and History opens
 * over Voice, whose back returns here.
 */
fun VoiceViewModel.onOpenHistory() {
    onStop()
    navigate(HistoryEntry)
}

/**
 * The Settings button: the phases stop and Settings opens over Voice, whose back returns here.
 */
fun VoiceViewModel.onOpenSettings() {
    onStop()
    navigate(SettingsEntry)
}

/**
 * "Aa", the prototype's `voiceToText`: the phases stop and the session opens as text over Voice, its
 * back returning here. Before the first transcript there is no conversation yet, so it opens a new
 * chat.
 */
fun VoiceViewModel.onOpenText() {
    onStop()
    navigate(ChatEntry(uiState.value.conversationId))
}
