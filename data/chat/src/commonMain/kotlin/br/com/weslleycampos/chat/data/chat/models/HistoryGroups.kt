package br.com.weslleycampos.chat.data.chat.models

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** A group of History's list, in the order History shows them: the prototype's `groupsOrder`. */
enum class HistorySection {
    /** Every pinned conversation, whatever its day. */
    PINNED,

    /** Last updated on the local date of "now". */
    TODAY,

    /** Last updated on the local date before it. */
    YESTERDAY,

    /** Last updated before yesterday. */
    EARLIER,
}

/** One group of History's list and its conversations, newest first. Never empty. */
data class HistoryGroup(
    val section: HistorySection,
    val conversations: List<Conversation>,
)

/**
 * History's groups for [conversations], as `ChatRepository.observeHistory` lists them: "pinned"
 * first, whatever the day, then "today", "yesterday" and "earlier" by the local date of each
 * conversation's [Conversation.updatedAt] in [timeZone], against [now]'s. Inside a group the most
 * recently updated comes first, the later id on a tie, whatever order [conversations] are in. A
 * group with no conversation is left out, so an empty list has no groups.
 *
 * A conversation updated after [now]'s date, as when the device's clock was moved back, counts as
 * today. Call it again when the date changes, so a conversation moves from "today" to "yesterday"
 * at midnight.
 */
fun groupHistory(conversations: List<Conversation>, now: Instant, timeZone: TimeZone): List<HistoryGroup> {
    val today = now.toLocalDateTime(timeZone).date
    val bySection = conversations
        .sortedWith(compareByDescending<Conversation> { it.updatedAt }.thenByDescending { it.id })
        .groupBy { it.section(today, timeZone) }
    return HistorySection.entries.mapNotNull { section ->
        bySection[section]?.let { HistoryGroup(section = section, conversations = it) }
    }
}

private fun Conversation.section(today: LocalDate, timeZone: TimeZone): HistorySection {
    if (pinned) return HistorySection.PINNED
    val day = updatedAt.toLocalDateTime(timeZone).date
    return when {
        day >= today -> HistorySection.TODAY
        day == today.minus(1, DateTimeUnit.DAY) -> HistorySection.YESTERDAY
        else -> HistorySection.EARLIER
    }
}
