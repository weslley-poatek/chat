package br.com.weslleycampos.chat.feature.history

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// History's text formats, as the prototype writes them: pure, so the tests pin each one. The clock
// time, the "Sep 13" date and the palette lookup repeat `:feature:chat`'s few lines rather than
// depend on another feature (see the spec's Decided).

/** The status row's place, which replaces the prototype's sync state: nothing syncs. */
private const val OnThisDevice = "on this device"

/** The prototype's separator, `" · "`. */
private const val Separator = " · "

private val MonthAbbreviations = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * A row's time, as the prototype's history items show it: local `HH:MM` on a 24-hour clock for a
 * conversation last updated today or yesterday in [timeZone] (or after today, when the device's
 * clock was moved back), else the month and day, "Sep 13". A pinned row follows its own day.
 */
fun historyRowTime(updatedAt: Instant, now: Instant, timeZone: TimeZone): String {
    val time = updatedAt.toLocalDateTime(timeZone)
    val yesterday = now.toLocalDateTime(timeZone).date.minus(1, DateTimeUnit.DAY)
    if (time.date >= yesterday) return "${time.hour.twoDigits()}:${time.minute.twoDigits()}"
    return "${MonthAbbreviations[time.month.ordinal]} ${time.date.day}"
}

/** The prototype's compare glyph, before the compared model in a row's extra. */
private const val CompareMark = "⇄"

/**
 * A row's extra while its conversation compares, the prototype's `extra:'⇄ gpt-5'`: the compare
 * mark and the second model's [shortName].
 */
fun historyCompareExtra(shortName: String): String = "$CompareMark $shortName"

/**
 * The status row, in place of the prototype's "synced · 2 min ago": nothing syncs, so it says where
 * History lives and how many conversations it lists, "on this device · 12 conversations". Just
 * "on this device" until the count is read.
 */
fun historyStatus(conversationCount: Int?): String = when (conversationCount) {
    null -> OnThisDevice
    0 -> "$OnThisDevice${Separator}no conversations"
    1 -> "$OnThisDevice${Separator}1 conversation"
    else -> "$OnThisDevice$Separator$conversationCount conversations"
}

/**
 * The accent of a model's dot: the `:core:ui` palette named after its id (`sonnet` → `SONNET`), or
 * the default for an id the palettes do not know, as the chat picks it.
 */
internal val ChatModel.palette: ChatPalette
    get() = ChatPalette.entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: ChatPalette.Default

/**
 * The device's clock and time zone, which History's days and times are read in. The time zone is
 * read on every use, so a change in the device's settings shows on the next regroup.
 */
class HistoryTimes(
    private val clock: Clock,
    private val zone: () -> TimeZone,
) {
    fun now(): Instant = clock.now()

    fun timeZone(): TimeZone = zone()

    /** Today's local date. */
    fun today(): LocalDate = now().toLocalDateTime(timeZone()).date

    /**
     * Today's local date now, and again whenever it changes, checked every [DayCheckInterval]:
     * nothing in the database changes at midnight, so this is what moves "today" to "yesterday".
     */
    fun days(): Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(DayCheckInterval)
        }
    }.distinctUntilChanged()

    companion object {
        /** How often [days] reads the date: a new day shows within a minute of midnight. */
        val DayCheckInterval: Duration = 1.minutes
    }
}

private fun Int.twoDigits(): String = toString().padStart(length = 2, padChar = '0')
