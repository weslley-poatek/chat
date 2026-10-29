package br.com.weslleycampos.chat.feature.voice

import br.com.weslleycampos.chat.core.ui.components.ChatVoicePhase
import br.com.weslleycampos.chat.data.chat.models.ReplyFailure
import br.com.weslleycampos.chat.data.chat.models.VoiceAnswer
import br.com.weslleycampos.chat.data.chat.models.VoiceExchange
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import br.com.weslleycampos.chat.data.chat.models.VoiceTurn
import br.com.weslleycampos.chat.feature.voice.speech.Heard
import br.com.weslleycampos.chat.feature.voice.speech.SpeechException
import br.com.weslleycampos.chat.feature.voice.speech.SpeechInput
import br.com.weslleycampos.chat.feature.voice.speech.SpeechOutput
import br.com.weslleycampos.chat.feature.voice.speech.SpeechProblem
import br.com.weslleycampos.chat.feature.voice.speech.voiceFor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the phases ask of the session as they go: the [VoicePlayer]'s side of the repository. */
internal interface VoiceTurns {
    /** Listening ended with [transcript]: saves it; `null` when nothing was saved. */
    suspend fun saveTranscript(transcript: String): VoiceTurn?

    /** The languages to listen and speak in, as Settings last picked them. */
    suspend fun languages(): VoiceLanguages

    /** Thinking: gets [turn]'s reply from Fuelix and saves it. */
    suspend fun answer(turn: VoiceTurn): VoiceAnswer

    /** Fuelix gave no reply: says why. */
    fun failed(failure: ReplyFailure)

    /** The device could not listen: says why. */
    fun unheard(problem: SpeechProblem)
}

/**
 * The prototype's phase machine, `startVoice` … `stopVoice` (lines 737–753), on [scope], on the
 * device's speech and Fuelix's replies, for as long as it is started. Each listen is for the
 * [VoiceTurns.languages] of the moment it starts, and each reply is spoken in the one it is written
 * in:
 *
 * - listening: the microphone is on ([SpeechInput]) and the caption shows the words as they are
 *   heard. It ends when the user stops speaking: when the device says so, or after
 *   [SilenceAfterSpeech] with no new word, or at a [tap]. Nothing heard, and it listens again. While
 *   muted the microphone is off and listening waits;
 * - thinking: the transcript is saved and [VoiceTurns.answer] gets and saves its reply, for as long
 *   as that takes. A tap while it lasts is forgotten;
 * - speaking: the device says the reply ([SpeechOutput]) and the caption shows each word as it is
 *   said, until the end or a [tap]. A device that cannot speak shows a word every [WordInterval]
 *   instead, for [speakingDuration].
 *
 * When no turn was saved, or its reply was dropped, listening starts again. When Fuelix gave no
 * reply, or the device cannot listen, [VoiceTurns.failed] or [VoiceTurns.unheard] says why and the
 * player stops at listening, so a missing key or a lost connection does not save one unanswered
 * transcript after another; a [tap] or the next [start] goes on.
 *
 * [start] begins at listening, whatever phase was shown before; [stop] turns the microphone off and
 * stops speaking where it is. Neither saves anything: a transcript being saved when the player stops
 * finishes on its own, and a reply being asked for is not asked for any longer.
 */
internal class VoicePlayer(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<VoiceUiState>,
    private val turns: VoiceTurns,
    private val input: SpeechInput,
    private val output: SpeechOutput,
) {
    /** One tap at most waits for the phase it ends: taps while a save finishes skip one phase, not several. */
    private val taps = Channel<Unit>(Channel.CONFLATED)

    private var job: Job? = null

    /** A failure stopped the player, rather than [stop]: a tap starts it again. */
    var halted = false
        private set

    /** The phases are playing. */
    val running: Boolean get() = job?.isActive == true

    /** Starts at listening, unless already playing. A tap from before the start is forgotten. */
    fun start() {
        if (running) return
        taps.tryReceive()
        job = scope.launch { play() }
    }

    /** Stops listening or speaking. */
    fun stop() {
        halted = false
        job?.cancel()
        job = null
    }

    /**
     * A tap on the sphere, the prototype's `voiceAdvance`: listening or speaking ends now. Once a
     * failure stopped the player, it starts it again.
     */
    fun tap() {
        if (running) taps.trySend(Unit) else if (halted) start()
    }

    private suspend fun play() {
        halted = false
        while (true) {
            show(ChatVoicePhase.Listening, exchange = null)
            val heard = try {
                hear()
            } catch (e: SpeechException) {
                halt()
                turns.unheard(e.problem)
                return
            }
            if (heard.isBlank()) {
                // Silence: listen again, after a breath so a recognizer that keeps failing does not spin.
                delay(ListenAgainPause)
                continue
            }
            show(ChatVoicePhase.Thinking, exchange = VoiceExchange(transcript = heard))
            val turn = turns.saveTranscript(heard)
            currentCoroutineContext().ensureActive()
            turn?.let { saved -> state.update { it.copy(exchange = saved.exchange) } }
            val answer = turn?.let { turns.answer(it) } ?: VoiceAnswer.Dropped
            currentCoroutineContext().ensureActive()
            taps.tryReceive()
            when (answer) {
                is VoiceAnswer.Spoken -> {
                    show(ChatVoicePhase.Speaking, exchange = turn?.exchange?.copy(reply = answer.reply))
                    say(answer.reply)
                }
                is VoiceAnswer.Failed -> {
                    halt()
                    turns.failed(answer.failure)
                    return
                }
                VoiceAnswer.Dropped -> Unit
            }
        }
    }

    /** Stops at listening, with nothing heard, for a tap to go on. */
    private fun halt() {
        show(ChatVoicePhase.Listening, exchange = null)
        halted = true
    }

    /**
     * Listens until the user stops speaking or a tap ends it, and returns what was heard. While muted
     * the microphone is off: muting stops listening, and it starts again, from nothing, once unmuted.
     */
    private suspend fun hear(): String {
        while (true) {
            state.first { !it.muted }
            listenOnce()?.let { return it }
            // Muting threw away what was heard: the caption starts again from nothing.
            state.update { it.copy(exchange = null, words = 0) }
        }
    }

    /** One listen: what was heard, or `null` when muting ended it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun listenOnce(): String? = coroutineScope {
        var latest = ""
        val heard = CompletableDeferred<String?>()
        val listening = launch {
            input.listen(turns.languages().all).collectLatest { words ->
                latest = words.text
                state.update { it.copy(exchange = VoiceExchange(transcript = latest), words = wordCount(latest)) }
                if (words is Heard.Final) {
                    heard.complete(latest)
                } else if (latest.isNotBlank()) {
                    delay(SilenceAfterSpeech)
                    heard.complete(latest)
                }
            }
            heard.complete(latest)
        }
        val tapped = launch {
            taps.receive()
            heard.complete(latest)
        }
        val muted = launch {
            state.first { it.muted }
            heard.complete(null)
        }
        heard.await().also {
            listening.cancel()
            tapped.cancel()
            muted.cancel()
        }
    }

    /** Says [reply], showing each word as it is said, until the end or a tap. */
    private suspend fun say(reply: String) = coroutineScope {
        val done = CompletableDeferred<Unit>()
        val speaking = launch {
            try {
                val voice = turns.languages().voiceFor(reply)
                output.speak(reply, voice).collect { spoken -> state.update { it.copy(words = spoken) } }
            } catch (ignored: SpeechException) {
                // No voice: the words show at the design's pace instead.
                withTimeoutOrNull(speakingDuration(reply)) {
                    while (true) {
                        delay(WordInterval)
                        state.update { it.copy(words = it.words + 1) }
                    }
                }
            }
            done.complete(Unit)
        }
        val tapped = launch {
            taps.receive()
            done.complete(Unit)
        }
        done.await()
        speaking.cancel()
        tapped.cancel()
    }

    private fun show(phase: ChatVoicePhase, exchange: VoiceExchange?) =
        state.update { it.copy(phase = phase, exchange = exchange, words = 0) }
}

/** How many words [text] has, split on spaces as the caption splits them; none for blank text. */
private fun wordCount(text: String): Int = if (text.isBlank()) 0 else text.split(' ').size
