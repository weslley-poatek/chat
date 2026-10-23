package br.com.weslleycampos.chat.feature.chat

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The read-aloud bar under a reply, the prototype's `reading`, `readPct` and `readPlaying`.
 *
 * @property messageId The reply being read.
 * @property ticks How many [ReadAloudTick]s it has played, up to [ReadAloudTicks].
 * @property playing "❚❚" while it plays, "▶" while it is paused or done.
 */
@Immutable
data class ReadAloudUi(val messageId: Long, val ticks: Int = 0, val playing: Boolean = true) {
    /** How full the bar is, 0 to 1. */
    val progress: Float get() = readAloudProgress(ticks)

    /** "0:08 / 0:17". */
    val label: String get() = readAloudLabel(ticks)

    /** The bar is full. */
    val finished: Boolean get() = ticks >= ReadAloudTicks
}

/**
 * Plays the read-aloud bar, the prototype's `startRead`, `toggleRead` and `stopRead`: one reply at
 * a time, a tick every [ReadAloudTick] while playing, stopping full with "▶". Nothing speaks: there
 * is no speech engine, so the bar and its time are the whole of it.
 *
 * @param onChange Shows the bar, or `null` once it is gone.
 */
class ReadAloudPlayer internal constructor(
    private val scope: CoroutineScope,
    private val onChange: (ReadAloudUi?) -> Unit,
) {
    private var shown: ReadAloudUi? = null
    private var ticking: Job? = null

    /** The reply being read, or `null`. */
    val messageId: Long? get() = shown?.messageId

    /** The bar's "▶" / "❚❚": pauses, plays on, or reads a finished reply again from the start. */
    fun onPlayPause() {
        val current = shown ?: return
        when {
            current.playing -> pause()
            current.finished -> play(ReadAloudUi(messageId = current.messageId))
            else -> play(current)
        }
    }

    /** The bar's "×": it stops and goes. */
    fun onStop() {
        ticking?.cancel()
        show(null)
    }

    /**
     * "read aloud" on [messageId]: reads it from the start, in place of any other reply, or plays or
     * pauses it if it is the one being read.
     */
    internal fun read(messageId: Long) {
        if (shown?.messageId == messageId) onPlayPause() else play(ReadAloudUi(messageId = messageId))
    }

    /** Holds the bar where it is, with "▶". */
    internal fun pause() {
        ticking?.cancel()
        shown?.let { show(it.copy(playing = false)) }
    }

    private fun play(from: ReadAloudUi) {
        ticking?.cancel()
        var current = from.copy(playing = true)
        show(current)
        ticking = scope.launch {
            while (!current.finished) {
                delay(ReadAloudTick)
                val ticks = current.ticks + 1
                current = current.copy(ticks = ticks, playing = ticks < ReadAloudTicks)
                show(current)
            }
        }
    }

    private fun show(reading: ReadAloudUi?) {
        shown = reading
        onChange(reading)
    }
}
