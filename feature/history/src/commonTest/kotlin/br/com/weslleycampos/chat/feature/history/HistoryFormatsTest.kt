package br.com.weslleycampos.chat.feature.history

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class HistoryFormatsTest {
    private val saoPaulo = TimeZone.of("America/Sao_Paulo")
    private val tokyo = TimeZone.of("Asia/Tokyo")

    private fun time(instant: String, now: Instant = Now, timeZone: TimeZone = TimeZone.UTC) =
        historyRowTime(updatedAt = Instant.parse(instant), now = now, timeZone = timeZone)

    @Test
    fun aRowShowsTheClockTimeForTodayAndYesterdayAndTheDateBeforeThat() {
        assertEquals("09:15", time("2026-09-14T09:15:00Z"))
        assertEquals("00:00", time("2026-09-14T00:00:00Z"))
        assertEquals("22:31", time("2026-09-13T22:31:00Z"))
        assertEquals("00:05", time("2026-09-13T00:05:00Z"))
        assertEquals("Sep 12", time("2026-09-12T23:59:00Z"))
        assertEquals("Sep 1", time("2026-09-01T08:00:00Z"))
        assertEquals("Jan 31", time("2026-01-31T08:00:00Z"))
        // The device's clock moved back: a later update is still today's.
        assertEquals("16:40", time("2026-09-15T16:40:00Z"))
    }

    @Test
    fun aRowsDayAndTimeAreLocalToTheGivenTimeZone() {
        // 01:30 UTC on Sep 14 is 22:30 on Sep 13 in São Paulo, where it is 11:02 on Sep 14.
        assertEquals("22:30", time("2026-09-14T01:30:00Z", timeZone = saoPaulo))
        assertEquals("01:30", time("2026-09-14T01:30:00Z"))
        // 16:00 UTC on Sep 12 is 01:00 on Sep 13 in Tokyo, where it is 23:02 on Sep 14: yesterday.
        assertEquals("01:00", time("2026-09-12T16:00:00Z", timeZone = tokyo))
        assertEquals("Sep 12", time("2026-09-12T16:00:00Z"))
        // Yesterday across a month's and a year's end.
        assertEquals("23:00", time("2026-08-31T23:00:00Z", now = Instant.parse("2026-09-01T08:00:00Z")))
        assertEquals("Dec 30", time("2025-12-30T23:00:00Z", now = Instant.parse("2026-01-01T08:00:00Z")))
    }

    @Test
    fun theStatusSaysWhereHistoryIsKeptAndHowManyConversationsItLists() {
        assertEquals("on this device", historyStatus(null))
        assertEquals("on this device · no conversations", historyStatus(0))
        assertEquals("on this device · 1 conversation", historyStatus(1))
        assertEquals("on this device · 12 conversations", historyStatus(12))
    }

    @Test
    fun everyModelHasThePaletteNamedAfterItsId() {
        for (model in ModelCatalogue.models) {
            assertEquals(model.id.uppercase(), model.palette.name)
        }
        val unknown = ChatModel(
            id = "gone",
            name = "Gone",
            shortName = "gone",
            provider = "Nobody",
            contextWindow = "1k",
            cost = "$",
            hue = 0,
        )
        assertEquals(ChatPalette.Default, unknown.palette)
    }

    @Test
    fun historyTimesReadTheDevicesClockAndTimeZoneOnEveryUse() {
        val clock = FixedClock()
        var zone: TimeZone = TimeZone.UTC
        val times = HistoryTimes(clock = clock, zone = { zone })

        assertEquals(Now, times.now())
        assertEquals(LocalDate(2026, 9, 14), times.today())
        zone = tokyo
        clock.now = Instant.parse("2026-09-14T20:00:00Z")
        assertEquals(tokyo, times.timeZone())
        assertEquals(LocalDate(2026, 9, 15), times.today())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theDaysEmitTodayAndTheNextDayWithinAMinuteOfMidnightOnlyOnce() = runTest {
        val clock = FixedClock(now = Instant.parse("2026-09-14T23:59:30Z"))
        val days = mutableListOf<LocalDate>()
        backgroundScope.launch { testTimes(clock).days().toList(days) }
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 9, 14)), days)

        clock.now = Instant.parse("2026-09-15T00:00:10Z")
        advanceTimeBy(HistoryTimes.DayCheckInterval - 1.seconds)
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 9, 14)), days)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 9, 14), LocalDate(2026, 9, 15)), days)

        // The same day again emits nothing.
        advanceTimeBy(HistoryTimes.DayCheckInterval * 3)
        runCurrent()
        assertEquals(listOf(LocalDate(2026, 9, 14), LocalDate(2026, 9, 15)), days)
    }
}
