package br.com.weslleycampos.chat.feature.voice

import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the phases ask of the session as they go: the [VoicePlayer]'s side of the repository. */
internal interface VoiceTurns {
    /** Listening starts: the exchange it plays, the session's next, once the session is read. */
    suspend fun next(): VoiceExchange

    /** Listening ended, by its timer or a tap: saves the transcript; `null` when nothing was saved. */
    suspend fun saveTranscript(): VoiceTurn?

    /** Thinking ended, by its timer or a tap: saves [turn]'s reply. */
    suspend fun saveReply(turn: VoiceTurn)
}

/**
 * The prototype's phase machine, `startVoice` … `stopVoice` (lines 737–753), on [scope]:
 * listening for 6500 ms, thinking for 2400 ms, speaking for 7000 ms, then listening again, for as
 * long as it is started. Each phase starts with no word shown and one more every [WordInterval]; a
 * [tap] ends the phase at once, and the next one gets its whole time.
 *
 * Listening plays [VoiceTurns.next]; as it ends the transcript is saved, and thinking and speaking
 * play the saved turn's exchange. As thinking ends the turn's reply is saved, before speaking
 * reveals it. When no turn was saved, thinking and speaking play the exchange listening played,
 * saving nothing, and the loop goes on.
 *
 * [start] begins at listening, whatever phase was shown before; [stop] ends the timers where they
 * are. Neither saves anything: a save under way when the player stops finishes on its own.
 */
internal class VoicePlayer(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<VoiceUiState>,
    private val turns: VoiceTurns,
) {
    /** One tap at most waits for the phase it ends: taps while a save finishes skip one phase, not several. */
    private val taps = Channel<Unit>(Channel.CONFLATED)

    private var job: Job? = null

    /** The phases are playing. */
    val running: Boolean get() = job?.isActive == true

    /** Starts at listening, unless already playing. A tap from before the start is forgotten. */
    fun start() {
        if (running) return
        taps.tryReceive()
        job = scope.launch { play() }
    }

    /** Stops the timers. */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** A tap on the sphere, the prototype's `voiceAdvance`: the phase ends now. Nothing while stopped. */
    fun tap() {
        if (running) taps.trySend(Unit)
    }

    private suspend fun play() {
        var phase = ChatVoicePhase.Listening
        var turn: VoiceTurn? = null
        while (true) {
            show(phase)
            when (phase) {
                ChatVoicePhase.Listening -> {
                    turn = null
                    val exchange = turns.next()
                    state.update { it.copy(exchange = exchange) }
                }
                ChatVoicePhase.Thinking -> {
                    turn = turns.saveTranscript()
                    currentCoroutineContext().ensureActive()
                    turn?.let { saved -> state.update { it.copy(exchange = saved.exchange) } }
                }
                ChatVoicePhase.Speaking -> turn?.let { turns.saveReply(it) }
            }
            reveal(phase)
            phase = phase.next
        }
    }

    private fun show(phase: ChatVoicePhase) = state.update { it.copy(phase = phase, words = 0) }

    /** Shows one more word every [WordInterval] until the phase's time is up or a tap ends it. */
    private suspend fun reveal(phase: ChatVoicePhase) = coroutineScope {
        val words = launch {
            while (true) {
                delay(WordInterval)
                state.update { it.copy(words = it.words + 1) }
            }
        }
        withTimeoutOrNull(phase.duration) { taps.receive() }
        words.cancel()
    }
}
