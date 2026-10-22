package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class ChatFormatsTest {
    private val saoPaulo = FixedOffsetTimeZone(UtcOffset(hours = -3))

    @Test
    fun clockTimesAreLocalTwentyFourHourHoursAndMinutes() {
        assertEquals("14:02", formatClockTime(Now, TimeZone.UTC))
        assertEquals("11:02", formatClockTime(Now, saoPaulo))
        assertEquals("00:05", formatClockTime(Instant.parse("2026-09-14T03:05:00Z"), saoPaulo))
        assertEquals("23:59", formatClockTime(Instant.parse("2026-09-14T23:59:59Z"), TimeZone.UTC))
    }

    @Test
    fun aRecentRowShowsTheTimeForTodayAndTheDateBeforeThat() {
        assertEquals("09:15", formatRecentTime(Instant.parse("2026-09-14T09:15:00Z"), Now, TimeZone.UTC))
        assertEquals("Sep 13", formatRecentTime(Instant.parse("2026-09-13T22:31:00Z"), Now, TimeZone.UTC))
        assertEquals("Sep 5", formatRecentTime(Instant.parse("2026-09-05T10:00:00Z"), Now, TimeZone.UTC))
        assertEquals("Jan 1", formatRecentTime(Instant.parse("2026-01-01T10:00:00Z"), Now, TimeZone.UTC))
        assertEquals("Dec 31", formatRecentTime(Instant.parse("2025-12-31T10:00:00Z"), Now, TimeZone.UTC))
    }

    @Test
    fun todayIsTheDeviceTimeZonesDay() {
        // 01:30 UTC on Sep 14 is still Sep 13 at UTC−3, where it is 11:02 on Sep 14 now.
        val lateLastNight = Instant.parse("2026-09-14T01:30:00Z")

        assertEquals("01:30", formatRecentTime(lateLastNight, Now, TimeZone.UTC))
        assertEquals("Sep 13", formatRecentTime(lateLastNight, Now, saoPaulo))
    }

    @Test
    fun latencyIsInTenthsOfASecondWithAHalfRoundingUp() {
        val cases = mapOf(
            0L to "0.0s",
            800L to "0.8s",
            849L to "0.8s",
            850L to "0.9s",
            999L to "1.0s",
            1_100L to "1.1s",
            5_240L to "5.2s",
            12_345L to "12.3s",
        )

        for ((millis, text) in cases) assertEquals(text, formatLatency(millis.milliseconds), "$millis ms")
    }

    @Test
    fun theReplyMetaJoinsModelTimeAndLatencyOrInterrupted() {
        fun meta(latency: String?, interrupted: String?) = assistantMeta(
            modelShortName = "sonnet-4.5",
            personaName = null,
            time = "14:02",
            latency = latency,
            interruptedLabel = interrupted,
        )

        assertEquals("sonnet-4.5 · 14:02 · 0.8s", meta(latency = "0.8s", interrupted = null))
        assertEquals("sonnet-4.5 · 14:02", meta(latency = null, interrupted = null))
        assertEquals("sonnet-4.5 · 14:02 · interrupted", meta(latency = null, interrupted = "interrupted"))
    }

    @Test
    fun theReplyMetaInsertsItsOwnPersonaLowercasedAfterTheShortName() {
        fun meta(persona: String, latency: String?, interrupted: String?) = assistantMeta(
            modelShortName = "sonnet-4.5",
            personaName = persona,
            time = "14:02",
            latency = latency,
            interruptedLabel = interrupted,
        )

        assertEquals(
            "sonnet-4.5 · code reviewer · 14:02 · 0.8s",
            meta(persona = "Code reviewer", latency = "0.8s", interrupted = null),
        )
        assertEquals(
            "sonnet-4.5 · paper digest · 14:02",
            meta(persona = "Paper digest", latency = null, interrupted = null),
        )
        assertEquals(
            "sonnet-4.5 · terse · 14:02 · interrupted",
            meta(persona = "Terse", latency = null, interrupted = "interrupted"),
        )
    }

    @Test
    fun theChipIsThePersonaAndItsShortNameOrTheModelsFullName() {
        assertEquals("Claude Sonnet 4.5", chipLabel(ModelChoice(ModelCatalogue.default)))
        assertEquals("Gemini 2.5 Pro", chipLabel(ModelChoice(Gemini)))
        assertEquals("Code reviewer · sonnet-4.5", chipLabel(ModelChoice(Reviewer)))
        assertEquals("Terse · gpt-5", chipLabel(ModelChoice(Terse)))
    }

    @Test
    fun theEmptyStateMetaEndsWithThePersonaWhenOneAnswers() {
        assertEquals("Anthropic · 200k context · $$", modelMetaLine(ModelCatalogue.default, persona = null))
        assertEquals("Google · 1M context · $$", modelMetaLine(Gemini, persona = null))
        assertEquals(
            "Anthropic · 200k context · $$ · persona: Code reviewer",
            modelMetaLine(Reviewer.model, persona = Reviewer),
        )
    }

    @Test
    fun aTipsStarredWordsAreItsEmphasisedRuns() {
        assertEquals(
            listOf("hold mic" to true, " to talk · " to false, "tap" to true, " for voice mode" to false),
            tipRuns("*hold mic* to talk · *tap* for voice mode"),
        )
        assertEquals(listOf("no emphasis" to false), tipRuns("no emphasis"))
    }

    @Test
    fun theComposerIsOneLinePerLineOfTheDraftFromOneToSix() {
        val cases = mapOf("" to 1, "one line" to 1, "two\nlines" to 2, "\n\n" to 3, "1\n2\n3\n4\n5\n6\n7\n8" to 6)

        for ((draft, rows) in cases) assertEquals(rows, draftRows(draft), draft)
    }

    @Test
    fun everyModelHasThePaletteNamedAfterItsId() {
        assertEquals(
            ModelCatalogue.models.map { it.id.uppercase() },
            ModelCatalogue.models.map { it.palette.name },
        )
        assertEquals(ChatPalette.GEMINI, Gemini.palette)
        assertEquals(ChatPalette.Default, ModelCatalogue.default.copy(id = "unknown").palette)
    }

    @Test
    fun chatTimesWriteInTheDevicesTimeZoneAgainstItsClock() {
        val clock = FixedClock()
        val times = ChatTimes(clock = clock, timeZone = { saoPaulo })
        val sent = Now - 1.hours

        assertEquals("10:02", times.messageTime(sent))
        assertEquals("10:02", times.recentTime(sent))
        clock.now = Now + 24.hours
        assertEquals("Sep 14", times.recentTime(sent))
    }
}
