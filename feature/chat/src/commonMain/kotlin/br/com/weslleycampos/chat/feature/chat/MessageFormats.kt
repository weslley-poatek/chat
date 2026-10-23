package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.data.chat.models.ForkOrigin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// The message action row's texts and the read-aloud bar's timing, as the prototype has them: pure,
// so the tests pin each one.

/** The read-aloud bar's timer, the prototype's `read` interval: a tick every 100 ms. */
val ReadAloudTick: Duration = 100.milliseconds

/** Each tick fills the bar by 0.6 %, the prototype's `readPct + 0.6`, here in tenths of a percent. */
const val ReadAloudPermillePerTick = 6

private const val FullPermille = 1000

/** Ticks until the bar is full: 167 (166 ticks are 99.6 %), 16.7 s of playing. */
const val ReadAloudTicks = (FullPermille + ReadAloudPermillePerTick - 1) / ReadAloudPermillePerTick

private const val MillisPerSecond = 1000
private const val SecondsPerMinute = 60

/** The prototype's `convTitle || 'untitled'`, for a source without a title. */
private const val UntitledSource = "untitled"

/** Between a reply's blocks when it is copied: a blank line. */
private const val BlockSeparator = "\n\n"

/**
 * What a message's "copy" puts on the clipboard, the prototype's `msgText`: the user's text as
 * sent, or a reply's blocks joined by a blank line, each paragraph and code block as its text (no
 * fence, no language), and a list as its items, "1. text", a line each.
 */
fun MessageUi.copyText(): String = when (this) {
    is MessageUi.User -> text
    is MessageUi.Assistant -> blocks.joinToString(BlockSeparator) { block ->
        when (block) {
            is BlockUi.Paragraph -> block.text
            is BlockUi.Code -> block.text
            is BlockUi.NumberedList -> block.items.joinToString("\n") { "${it.number} ${it.text}" }
        }
    }
}

/**
 * The fork banner's text after "⑂ ", the prototype's `forkFrom`: "forked from “useDebounce hook”
 * at message 2", "untitled" for a source with a blank title.
 */
fun forkBannerText(origin: ForkOrigin): String =
    "forked from “${origin.sourceTitle.ifBlank { UntitledSource }}” at message ${origin.messageNumber}"

/** How full the read-aloud bar is after [ticks], from 0 to 1: 0.6 % a tick, never past full. */
fun readAloudProgress(ticks: Int): Float =
    (ticks * ReadAloudPermillePerTick).coerceIn(0, FullPermille) / FullPermille.toFloat()

/**
 * The read-aloud bar's time, "0:08 / 0:17": how long it has played of the [ReadAloudTicks] it
 * takes to fill, both from its own ticks, so it reaches "0:17 / 0:17" as the bar fills. The
 * prototype's fixed "0:41" ran out of step with its bar (frames quirk 3).
 */
fun readAloudLabel(ticks: Int): String {
    val played = formatReadTime(ReadAloudTick * ticks.coerceIn(0, ReadAloudTicks))
    return "$played / ${formatReadTime(ReadAloudTick * ReadAloudTicks)}"
}

/** "m:ss", to the nearest second with a half rounding up, as the prototype's `Math.round`. */
fun formatReadTime(time: Duration): String {
    val seconds = (time.inWholeMilliseconds + MillisPerSecond / 2) / MillisPerSecond
    val minutes = seconds / SecondsPerMinute
    return "$minutes:${(seconds % SecondsPerMinute).toString().padStart(length = 2, padChar = '0')}"
}
