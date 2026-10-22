package br.com.weslleycampos.chat.data.chat.models

import br.com.weslleycampos.chat.data.chat.models.HistorySection.EARLIER
import br.com.weslleycampos.chat.data.chat.models.HistorySection.PINNED
import br.com.weslleycampos.chat.data.chat.models.HistorySection.TODAY
import br.com.weslleycampos.chat.data.chat.models.HistorySection.YESTERDAY
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class HistoryGroupsTest {
    private val saoPaulo = TimeZone.of("America/Sao_Paulo")
    private val tokyo = TimeZone.of("Asia/Tokyo")
    private val berlin = TimeZone.of("Europe/Berlin")

    private fun conversation(id: Long, updatedAt: String, pinned: Boolean = false) = Conversation(
        id = id,
        title = "conversation $id",
        model = ModelCatalogue.default,
        createdAt = Instant.parse(updatedAt),
        updatedAt = Instant.parse(updatedAt),
        pinned = pinned,
    )

    /** Each group's section and the ids in it, in order. */
    private fun groups(conversations: List<Conversation>, now: String, timeZone: TimeZone = TimeZone.UTC) =
        groupHistory(conversations, Instant.parse(now), timeZone).map { group ->
            group.section to group.conversations.map(Conversation::id)
        }

    @Test
    fun pinnedComesFirstWhateverItsDayThenTodayYesterdayAndEarlier() {
        val conversations = listOf(
            conversation(8, "2026-09-12T10:00:00Z"),
            conversation(2, "2026-09-14T11:48:00Z"),
            conversation(7, "2026-09-10T09:00:00Z", pinned = true),
            conversation(4, "2026-09-13T22:31:00Z"),
            conversation(1, "2026-09-14T14:02:00Z", pinned = true),
            conversation(3, "2026-09-14T09:15:00Z"),
            conversation(6, "2026-09-13T13:20:00Z"),
        )

        assertEquals(
            listOf(
                PINNED to listOf(1L, 7L),
                TODAY to listOf(2L, 3L),
                YESTERDAY to listOf(4L, 6L),
                EARLIER to listOf(8L),
            ),
            groups(conversations, now = "2026-09-14T14:05:00Z"),
        )
    }

    @Test
    fun aGroupWithNoConversationIsLeftOutAndNoConversationsAreNoGroups() {
        val conversations = listOf(conversation(1, "2026-09-14T09:00:00Z"), conversation(2, "2026-09-01T09:00:00Z"))

        assertEquals(listOf(TODAY to listOf(1L), EARLIER to listOf(2L)), groups(conversations, "2026-09-14T14:05:00Z"))
        assertEquals(emptyList(), groups(emptyList(), now = "2026-09-14T14:05:00Z"))
    }

    @Test
    fun insideAGroupTheMostRecentlyUpdatedComesFirstAndTheLaterIdOnATie() {
        val conversations = listOf(
            conversation(1, "2026-09-14T09:00:00Z"),
            conversation(3, "2026-09-14T10:00:00Z"),
            conversation(2, "2026-09-14T10:00:00Z"),
            conversation(4, "2026-09-14T08:00:00Z"),
        )

        assertEquals(listOf(TODAY to listOf(3L, 2L, 1L, 4L)), groups(conversations, now = "2026-09-14T14:05:00Z"))
    }

    @Test
    fun theDayIsTheLocalDateOfTheLastMessageInTheGivenTimeZone() {
        // 02:30 UTC is 23:30 the evening before in São Paulo and 11:30 the same morning in Tokyo.
        val now = "2026-10-22T02:30:00Z"
        val conversations = listOf(
            conversation(1, "2026-10-22T01:00:00Z"),
            conversation(2, "2026-10-21T23:00:00Z"),
            conversation(3, "2026-10-21T02:00:00Z"),
            conversation(4, "2026-10-20T12:00:00Z"),
        )

        assertEquals(
            listOf(TODAY to listOf(1L), YESTERDAY to listOf(2L, 3L), EARLIER to listOf(4L)),
            groups(conversations, now, TimeZone.UTC),
        )
        assertEquals(
            listOf(TODAY to listOf(1L, 2L), YESTERDAY to listOf(3L, 4L)),
            groups(conversations, now, saoPaulo),
        )
        assertEquals(
            listOf(TODAY to listOf(1L, 2L), YESTERDAY to listOf(3L), EARLIER to listOf(4L)),
            groups(conversations, now, tokyo),
        )
    }

    @Test
    fun atLocalMidnightTodayBecomesYesterdayAndAMidnightLaterEarlier() {
        // 23:59:59 on Oct 21 in São Paulo (UTC−3).
        val conversations = listOf(conversation(1, "2026-10-22T02:59:59Z"))

        assertEquals(listOf(TODAY to listOf(1L)), groups(conversations, "2026-10-22T02:59:59.999Z", saoPaulo))
        assertEquals(listOf(YESTERDAY to listOf(1L)), groups(conversations, "2026-10-22T03:00:00Z", saoPaulo))
        assertEquals(listOf(YESTERDAY to listOf(1L)), groups(conversations, "2026-10-23T02:59:59Z", saoPaulo))
        assertEquals(listOf(EARLIER to listOf(1L)), groups(conversations, "2026-10-23T03:00:00Z", saoPaulo))
    }

    @Test
    fun yesterdayIsTheDayBeforeAcrossTheEndOfAMonthAndOfAYear() {
        val newYear = listOf(conversation(1, "2026-12-31T20:00:00Z"), conversation(2, "2026-12-30T20:00:00Z"))
        val march = listOf(conversation(1, "2027-02-28T20:00:00Z"), conversation(2, "2027-02-27T20:00:00Z"))

        assertEquals(
            listOf(YESTERDAY to listOf(1L), EARLIER to listOf(2L)),
            groups(newYear, now = "2027-01-01T08:00:00Z"),
        )
        assertEquals(
            listOf(YESTERDAY to listOf(1L), EARLIER to listOf(2L)),
            groups(march, now = "2027-03-01T08:00:00Z"),
        )
    }

    @Test
    fun theDayTheClocksGoBackIsStillOneDay() {
        // Berlin leaves summer time at 03:00 on Oct 25, 2026; at 00:30 on Oct 26 that whole day is yesterday.
        val conversations = listOf(
            // 00:30 and 23:30 on Oct 25, in summer and in winter time.
            conversation(1, "2026-10-24T22:30:00Z"),
            conversation(2, "2026-10-25T22:30:00Z"),
            // 23:30 on Oct 24.
            conversation(3, "2026-10-24T21:30:00Z"),
        )

        assertEquals(
            listOf(YESTERDAY to listOf(2L, 1L), EARLIER to listOf(3L)),
            groups(conversations, now = "2026-10-25T23:30:00Z", berlin),
        )
    }

    @Test
    fun aConversationUpdatedAfterNowCountsAsToday() {
        // The device's clock was moved back a day after this message was sent.
        val conversations = listOf(conversation(1, "2026-09-15T09:00:00Z"))

        assertEquals(listOf(TODAY to listOf(1L)), groups(conversations, now = "2026-09-14T14:05:00Z"))
    }
}
