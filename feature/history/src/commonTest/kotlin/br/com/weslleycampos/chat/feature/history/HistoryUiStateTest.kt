package br.com.weslleycampos.chat.feature.history

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.HistorySection
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.feature.history.resources.FeatureHistoryRes
import br.com.weslleycampos.chat.feature.history.resources.history_action_archive
import br.com.weslleycampos.chat.feature.history.resources.history_action_delete
import br.com.weslleycampos.chat.feature.history.resources.history_action_pin
import br.com.weslleycampos.chat.feature.history.resources.history_action_unpin
import br.com.weslleycampos.chat.feature.history.resources.history_section_earlier
import br.com.weslleycampos.chat.feature.history.resources.history_section_pinned
import br.com.weslleycampos.chat.feature.history.resources.history_section_today
import br.com.weslleycampos.chat.feature.history.resources.history_section_yesterday
import br.com.weslleycampos.chat.feature.history.resources.history_toast_archived
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class HistoryUiStateTest {
    private fun conversation(
        id: Long,
        title: String,
        updatedAt: Instant,
        modelId: String = ModelCatalogue.DEFAULT_MODEL_ID,
        pinned: Boolean = false,
    ) = Conversation(
        id = id,
        title = title,
        model = ModelCatalogue[modelId],
        createdAt = updatedAt,
        updatedAt = updatedAt,
        pinned = pinned,
    )

    private val row = HistoryRowUi(
        id = 1,
        title = "useDebounce hook",
        pinned = false,
        modelShortName = "sonnet-4.5",
        palette = ChatPalette.SONNET,
        time = "14:02",
    )

    private val group = HistoryGroupUi(section = HistorySection.TODAY, rows = listOf(row))

    @Test
    fun theGroupsArePinnedTodayYesterdayAndEarlierUnderTheirLowercaseLabels() {
        val groups = historyGroups(
            conversations = listOf(
                conversation(id = 1, title = "Terraform module", updatedAt = Now - 3.days),
                conversation(id = 2, title = "SSE vs WebSocket", updatedAt = Now - 20.hours),
                conversation(id = 3, title = "Postgres index", updatedAt = Now - 2.hours),
                conversation(id = 4, title = "RFC: memory retention", updatedAt = Now - 2.days, pinned = true),
                conversation(id = 5, title = "useDebounce hook", updatedAt = Now),
            ),
            now = Now,
            timeZone = TimeZone.UTC,
        )

        assertEquals(
            listOf(HistorySection.PINNED, HistorySection.TODAY, HistorySection.YESTERDAY, HistorySection.EARLIER),
            groups.map { it.section },
        )
        assertEquals(
            listOf(
                FeatureHistoryRes.string.history_section_pinned,
                FeatureHistoryRes.string.history_section_today,
                FeatureHistoryRes.string.history_section_yesterday,
                FeatureHistoryRes.string.history_section_earlier,
            ),
            groups.map { it.label },
        )
        assertEquals(
            listOf(listOf(4L), listOf(5L, 3L), listOf(2L), listOf(1L)),
            groups.map { it.rows.map(HistoryRowUi::id) },
        )
        assertTrue(historyGroups(conversations = emptyList(), now = Now, timeZone = TimeZone.UTC).isEmpty())
    }

    @Test
    fun aRowShowsThePinMarkItsTitleItsModelsDotAndShortNameAndItsTime() {
        val pinned = conversation(id = 4, title = "RFC: memory retention", updatedAt = Now - 2.days, pinned = true)
            .toRowUi(now = Now, timeZone = TimeZone.UTC)
        val gemini = conversation(id = 5, title = "SSE vs WebSocket", updatedAt = Now - 20.hours, modelId = "gemini")
            .toRowUi(now = Now, timeZone = TimeZone.UTC)

        assertEquals(
            HistoryRowUi(
                id = 4,
                title = "RFC: memory retention",
                pinned = true,
                modelShortName = "sonnet-4.5",
                palette = ChatPalette.SONNET,
                time = "Sep 12",
            ),
            pinned,
        )
        assertEquals(
            HistoryRowUi(
                id = 5,
                title = "SSE vs WebSocket",
                pinned = false,
                modelShortName = "gemini-2.5-pro",
                palette = ChatPalette.GEMINI,
                time = "18:02",
            ),
            gemini,
        )
    }

    @Test
    fun aComparingConversationsRowShowsTheComparedModelAsItsExtraAndOtherRowsNone() {
        val base = conversation(id = 4, title = "SSE vs WebSocket", updatedAt = Now - 20.hours, modelId = "gemini")
        val comparing = base.copy(compareOn = true, compareWith = ModelCatalogue["gpt5"])
            .toRowUi(now = Now, timeZone = TimeZone.UTC)
        val pickingSecond = base.copy(compareOn = true).toRowUi(now = Now, timeZone = TimeZone.UTC)
        val stale = base.copy(compareOn = false, compareWith = ModelCatalogue["gpt5"])
            .toRowUi(now = Now, timeZone = TimeZone.UTC)

        assertEquals("gpt-5", comparing.comparedWith)
        assertEquals("⇄ gpt-5", comparing.extra)
        assertEquals("gemini-2.5-pro", comparing.modelShortName)
        // Compare on without a second model, or a second model left over with compare off: no extra.
        assertNull(pickingSecond.extra)
        assertNull(stale.extra)
        assertNull(row.extra)
    }

    @Test
    fun aRowOffersPinOrUnpinThenArchiveAndDeleteToTouchAndScreenReadersAlike() {
        assertEquals(
            listOf(
                HistoryRowActionUi(HistoryRowAction.TogglePin, FeatureHistoryRes.string.history_action_pin),
                HistoryRowActionUi(HistoryRowAction.Archive, FeatureHistoryRes.string.history_action_archive),
                HistoryRowActionUi(HistoryRowAction.Delete, FeatureHistoryRes.string.history_action_delete),
            ),
            row.actions,
        )
        assertEquals(FeatureHistoryRes.string.history_action_unpin, row.copy(pinned = true).actions.first().label)
        assertEquals(HistoryRowAction.entries, row.actions.map { it.action })
    }

    @Test
    fun noMatchesNeedsAReadEmptyListAndAQueryWhileTheEmptyStateNeedsNone() {
        val searched = HistoryUiState(query = "  kubernetes ", loaded = true)
        val firstLaunch = HistoryUiState(loaded = true)

        assertEquals("kubernetes", searched.noMatchesFor)
        assertFalse(searched.showsEmptyState)
        assertNull(firstLaunch.noMatchesFor)
        assertTrue(firstLaunch.showsEmptyState)
        // Before the list is read there is nothing to say yet, and a blank query is no search.
        assertNull(HistoryUiState(query = "kubernetes").noMatchesFor)
        assertFalse(HistoryUiState().showsEmptyState)
        assertTrue(HistoryUiState(query = "   ", loaded = true).showsEmptyState)
        assertNull(HistoryUiState(query = "debounce", groups = listOf(group), loaded = true).noMatchesFor)
    }

    @Test
    fun theFooterHintShowsUnderRowsOnly() {
        assertTrue(HistoryUiState(groups = listOf(group), loaded = true).showsFooterHint)
        assertFalse(HistoryUiState(loaded = true).showsFooterHint)
        assertFalse(HistoryUiState(query = "kubernetes", loaded = true).showsFooterHint)
    }

    @Test
    fun theClearMarkShowsWhileTheBoxHasText() {
        assertFalse(HistoryUiState().showsClearQuery)
        assertTrue(HistoryUiState(query = " ").showsClearQuery)
        assertTrue(HistoryUiState(query = "gpt").showsClearQuery)
    }

    @Test
    fun aRowIsFoundByItsIdInWhicheverGroupItIs() {
        val pinned = HistoryGroupUi(section = HistorySection.PINNED, rows = listOf(row.copy(id = 9, pinned = true)))
        val state = HistoryUiState(groups = listOf(pinned, group))

        assertEquals(9L, state.row(9)?.id)
        assertEquals(row, state.row(1))
        assertNull(state.row(2))
    }

    @Test
    fun theActionsWearTheDesignsColoursInEveryPalette() {
        for (palette in ChatPalette.entries) {
            for (dark in listOf(false, true)) {
                val colors = palette.colors(dark)

                assertEquals(
                    colors.surface.tertiary to colors.text.primary,
                    historyRowActionColors(HistoryRowAction.TogglePin, colors),
                )
                assertEquals(
                    colors.text.tertiary to colors.surface.primary,
                    historyRowActionColors(HistoryRowAction.Archive, colors),
                )
                assertEquals(
                    colors.status.danger to colors.surface.primary,
                    historyRowActionColors(HistoryRowAction.Delete, colors),
                )
            }
        }
    }

    @Test
    fun previewsCoverEveryStateInLightAndDark() {
        val cases = HistoryScreenPreviewParameterProvider().values.toList()
        val names = listOf(
            "Grouped",
            "Filtered",
            "No matches",
            "Empty",
            "Swiped open",
            "Swiped open · pinned",
            "Toast",
        )

        assertEquals(names + names, cases.map { it.name })
        assertEquals(List(names.size) { false } + List(names.size) { true }, cases.map { it.darkTheme })
        val states = cases.take(names.size).associate { it.name to it.uiState }
        assertEquals(
            listOf(HistorySection.PINNED, HistorySection.TODAY, HistorySection.YESTERDAY, HistorySection.EARLIER),
            states.getValue("Grouped").groups.map { it.section },
        )
        assertTrue(states.getValue("Grouped").groups.first().rows.all { it.pinned })
        assertEquals("⇄ gpt-5", states.getValue("Grouped").row(4)?.extra)
        val comparing = states.getValue("Grouped").groups.flatMap { it.rows }.filter { it.extra != null }
        assertEquals(listOf(4L), comparing.map { it.id })
        val filtered = states.getValue("Filtered")
        assertTrue(filtered.showsClearQuery)
        assertTrue(filtered.groups.flatMap { it.rows }.all { it.modelShortName.contains("gpt") })
        assertEquals("kubernetes", states.getValue("No matches").noMatchesFor)
        assertTrue(states.getValue("Empty").showsEmptyState)
        val open = states.getValue("Swiped open")
        assertFalse(assertNotNull(open.openRow?.let(open::row)).pinned)
        val openPinned = states.getValue("Swiped open · pinned")
        assertTrue(assertNotNull(openPinned.openRow?.let(openPinned::row)).pinned)
        assertEquals(FeatureHistoryRes.string.history_toast_archived, states.getValue("Toast").toast)
    }
}
