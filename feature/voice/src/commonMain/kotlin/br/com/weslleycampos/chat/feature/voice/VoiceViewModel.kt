package br.com.weslleycampos.chat.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.VoiceRepository
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.data.chat.models.VoiceSession
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

/**
 * The Voice mode frame's logic, the home screen: the prototype's phase machine (`startVoice` …
 * `stopVoice`, lines 737–753), mute and the model chip, on [VoiceRepository]. There is no
 * microphone and no model, so the phases run on [VoicePlayer]'s timer while the screen is resumed
 * ([onStart] / [onStop]), and every exchange is saved as it is heard: the transcript when listening
 * ends, the reply when thinking ends. A voice session is therefore an ordinary conversation, listed
 * in History and readable as text.
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
            override suspend fun next(): VoiceExchange = session
                .filterNotNull()
                .first { it.conversationId == uiState.value.conversationId }
                .next

            override suspend fun saveTranscript(): VoiceTurn? = saveTranscriptOnce()

            override suspend fun saveReply(turn: VoiceTurn) {
                attempt(fallback = false) { voices.saveReply(turn) }
            }
        },
    )

    init {
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
     * The screen is resumed, the prototype's entering voice mode: the phases start at listening on
     * the session's next exchange.
     */
    fun onStart() = player.start()

    /**
     * The screen is paused or left, for History, the text, another app or a rotation, the
     * prototype's leaving voice mode: the timers stop. Nothing is saved or undone: a phase cut
     * short keeps what was saved before it.
     */
    fun onStop() = player.stop()

    /** A tap on the sphere, the prototype's `voiceAdvance`: the next phase now, with its whole time. */
    fun onSphereTap() = player.tap()

    /** Mute, the prototype's `toggleMute`: only the listening sphere is stilled; the phases go on. */
    fun onToggleMute() = uiState.update { it.copy(muted = !it.muted) }

    /**
     * "end", the prototype's `exitVoice`: the session closes. What it saved stays, a conversation in
     * History; a fresh session starts at listening on the default model, whatever conversation the
     * entry opened.
     */
    fun onEnd() {
        val running = player.running
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
     * Saves the next transcript and, on the first one of a new session, moves the session to the
     * conversation it created. It runs to the end even when the screen leaves meanwhile, so the
     * session never loses the conversation its transcript went into.
     */
    private suspend fun saveTranscriptOnce(): VoiceTurn? = withContext(NonCancellable) {
        val from = generation
        val conversationId = uiState.value.conversationId
        val newSession = picker.pending.takeIf { conversationId == null }
        val turn = attempt(fallback = null) { voices.saveTranscript(conversationId, newSession) }
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
 * The History button, where the prototype's back chevron was: the timers stop and History opens
 * over Voice, whose back returns here.
 */
fun VoiceViewModel.onOpenHistory() {
    onStop()
    navigate(HistoryEntry)
}

/**
 * "Aa", the prototype's `voiceToText`: the timers stop and the session opens as text over Voice, its
 * back returning here. Before the first transcript there is no conversation yet, so it opens a new
 * chat.
 */
fun VoiceViewModel.onOpenText() {
    onStop()
    navigate(ChatEntry(uiState.value.conversationId))
}
