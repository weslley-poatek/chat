package br.com.weslleycampos.chat.feature.history

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavigatorImpl
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.core.navigation.entries.settings.SettingsEntry
import br.com.weslleycampos.chat.core.navigation.entries.voice.VoiceEntry
import br.com.weslleycampos.chat.data.chat.models.HistorySection
import br.com.weslleycampos.chat.feature.history.resources.FeatureHistoryRes
import br.com.weslleycampos.chat.feature.history.resources.history_action_pin
import br.com.weslleycampos.chat.feature.history.resources.history_action_unpin
import br.com.weslleycampos.chat.feature.history.resources.history_toast_archived
import br.com.weslleycampos.chat.feature.history.resources.history_toast_deleted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val viewModels = mutableListOf<HistoryViewModel>()

    /**
     * `runTest`, after which the screen leaves: each view model's scope ends, and with it the
     * minute-by-minute day check, which `runTest` would otherwise run forever on virtual time.
     */
    private fun historyTest(body: suspend TestScope.() -> Unit): TestResult = runTest {
        try {
            body()
        } finally {
            viewModels.forEach { it.viewModelScope.cancel() }
        }
    }

    private fun TestScope.historyViewModel(
        repository: FakeChatRepository = FakeChatRepository(),
        clock: FixedClock = FixedClock(),
        backStack: List<NavKey> = listOf(VoiceEntry(), HistoryEntry),
        read: Boolean = true,
    ): HistoryViewModel {
        val viewModel = HistoryViewModel(
            navigator = NavigatorImpl(NavBackStack<NavKey>().apply { addAll(backStack) }),
            repository = repository,
            times = testTimes(clock),
        )
        viewModels += viewModel
        if (read) runCurrent()
        return viewModel
    }

    private val HistoryViewModel.state: HistoryUiState get() = uiState.value

    private val HistoryViewModel.stack: List<NavKey> get() = navigator.navBackStack.toList()

    private val HistoryUiState.sections: List<HistorySection> get() = groups.map { it.section }

    private val HistoryUiState.titles: List<List<String>> get() = groups.map { group -> group.rows.map { it.title } }

    private fun TestScope.advance(time: Duration) {
        advanceTimeBy(time)
        runCurrent()
    }

    // The list

    @Test
    fun historyListsEveryConversationInItsGroupNewestFirstWithTheCount() = historyTest {
        val repository = FakeChatRepository()
        repository.seed("Terraform module", model = Llama, updatedAt = Now - 3.days)
        repository.seed("SSE vs WebSocket", model = Gemini, updatedAt = Now - 15.hours - 31.minutes)
        repository.seed("Postgres index", model = Gpt5, updatedAt = Now - 2.hours - 14.minutes)
        repository.seed("useDebounce hook", pinned = true)

        val state = historyViewModel(repository).state

        assertTrue(state.loaded)
        assertEquals(
            listOf(HistorySection.PINNED, HistorySection.TODAY, HistorySection.YESTERDAY, HistorySection.EARLIER),
            state.sections,
        )
        assertEquals(
            listOf(
                listOf("useDebounce hook"),
                listOf("Postgres index"),
                listOf("SSE vs WebSocket"),
                listOf("Terraform module"),
            ),
            state.titles,
        )
        assertEquals(
            listOf("14:02", "11:48", "22:31", "Sep 11"),
            state.groups.flatMap { group -> group.rows.map { it.time } },
        )
        assertEquals(4, state.conversationCount)
        assertEquals("on this device · 4 conversations", state.status)
        assertTrue(state.showsFooterHint)
        assertFalse(state.showsEmptyState)
        assertNull(state.noMatchesFor)
    }

    @Test
    fun aQueryFiltersTheListAtOnceAndClearingItListsEverythingAgain() = historyTest {
        val repository = FakeChatRepository()
        repository.seed("Postgres index", model = Gpt5, updatedAt = Now - 1.hours)
        repository.seed("useDebounce hook")
        val viewModel = historyViewModel(repository)

        viewModel.onQueryChange("gpt")
        assertEquals("gpt", viewModel.state.query)
        assertTrue(viewModel.state.showsClearQuery)
        runCurrent()
        assertEquals(listOf(listOf("Postgres index")), viewModel.state.titles)

        // Trailing spaces search the same text again, so nothing re-runs.
        viewModel.onQueryChange("gpt ")
        runCurrent()
        assertEquals("gpt ", viewModel.state.query)

        viewModel.onClearQuery()
        assertEquals("", viewModel.state.query)
        assertFalse(viewModel.state.showsClearQuery)
        runCurrent()
        assertEquals(listOf(listOf("useDebounce hook", "Postgres index")), viewModel.state.titles)
        assertEquals(listOf("", "gpt", ""), repository.queries)
        // The status counts every conversation whatever the query.
        assertEquals(2, viewModel.state.conversationCount)
    }

    @Test
    fun aQueryThatMatchesNothingSaysSoWithTheTrimmedQueryAndNoHint() = historyTest {
        val repository = FakeChatRepository()
        repository.seed("useDebounce hook")
        val viewModel = historyViewModel(repository)

        viewModel.onQueryChange("  kubernetes ")
        runCurrent()

        assertEquals("kubernetes", viewModel.state.noMatchesFor)
        assertFalse(viewModel.state.showsEmptyState)
        assertFalse(viewModel.state.showsFooterHint)
        assertEquals(1, viewModel.state.conversationCount)
    }

    @Test
    fun firstLaunchShowsTheEmptyStateOnceTheListIsReadAndNothingBefore() = historyTest {
        val viewModel = historyViewModel(read = false)

        assertFalse(viewModel.state.loaded)
        assertFalse(viewModel.state.showsEmptyState)
        assertNull(viewModel.state.noMatchesFor)
        assertEquals("on this device", viewModel.state.status)

        runCurrent()
        assertTrue(viewModel.state.showsEmptyState)
        assertNull(viewModel.state.noMatchesFor)
        assertFalse(viewModel.state.showsFooterHint)
        assertEquals("on this device · no conversations", viewModel.state.status)
    }

    @Test
    fun theGroupsMoveOnWhenTheDateChangesWhileTheScreenIsOpen() = historyTest {
        val clock = FixedClock(now = Instant.parse("2026-09-14T23:59:30Z"))
        val repository = FakeChatRepository()
        repository.seed("useDebounce hook", updatedAt = Instant.parse("2026-09-14T23:30:00Z"))
        val viewModel = historyViewModel(repository, clock)
        assertEquals(listOf(HistorySection.TODAY), viewModel.state.sections)

        clock.now = Instant.parse("2026-09-15T00:00:10Z")
        advance(HistoryTimes.DayCheckInterval)
        assertEquals(listOf(HistorySection.YESTERDAY), viewModel.state.sections)
        assertEquals("23:30", viewModel.state.groups.single().rows.single().time)

        clock.now = Instant.parse("2026-09-16T00:00:10Z")
        advance(HistoryTimes.DayCheckInterval)
        assertEquals(listOf(HistorySection.EARLIER), viewModel.state.sections)
        assertEquals("Sep 14", viewModel.state.groups.single().rows.single().time)
    }

    // Swipe

    @Test
    fun onlyOneRowIsOpenAtATime() = historyTest {
        val repository = FakeChatRepository()
        val first = repository.seed("Postgres index", updatedAt = Now - 1.hours)
        val second = repository.seed("useDebounce hook")
        val viewModel = historyViewModel(repository)

        viewModel.onSwipeStart(first.id)
        viewModel.onSwipeSettle(first.id, open = true)
        assertEquals(first.id, viewModel.state.openRow)

        // Dragging the open row keeps it open; starting on another closes it.
        viewModel.onSwipeStart(first.id)
        assertEquals(first.id, viewModel.state.openRow)
        viewModel.onSwipeStart(second.id)
        assertNull(viewModel.state.openRow)

        viewModel.onSwipeSettle(second.id, open = true)
        assertEquals(second.id, viewModel.state.openRow)
        // A row released closed that was not the open one leaves the open one alone.
        viewModel.onSwipeSettle(first.id, open = false)
        assertEquals(second.id, viewModel.state.openRow)
        viewModel.onSwipeSettle(second.id, open = false)
        assertNull(viewModel.state.openRow)
    }

    @Test
    fun aRowThatLeavesTheListIsNoLongerOpen() = historyTest {
        val repository = FakeChatRepository()
        val postgres = repository.seed("Postgres index", model = Gpt5)
        val viewModel = historyViewModel(repository)
        viewModel.onSwipeSettle(postgres.id, open = true)

        viewModel.onQueryChange("debounce")
        runCurrent()

        assertNull(viewModel.state.openRow)
    }

    // Actions

    @Test
    fun pinMovesARowToPinnedAndUnpinBringsItBackEachClosingTheRow() = historyTest {
        val repository = FakeChatRepository()
        val postgres = repository.seed("Postgres index", updatedAt = Now - 1.hours)
        repository.seed("useDebounce hook")
        val viewModel = historyViewModel(repository)
        viewModel.onSwipeSettle(postgres.id, open = true)

        viewModel.onRowAction(postgres.id, HistoryRowAction.TogglePin)
        assertNull(viewModel.state.openRow)
        runCurrent()
        assertEquals(listOf(HistorySection.PINNED, HistorySection.TODAY), viewModel.state.sections)
        assertEquals(listOf(listOf("Postgres index"), listOf("useDebounce hook")), viewModel.state.titles)
        val pinned = viewModel.state.groups.first().rows.single()
        assertTrue(pinned.pinned)
        assertEquals(FeatureHistoryRes.string.history_action_unpin, pinned.actions.first().label)

        viewModel.onSwipeSettle(postgres.id, open = true)
        viewModel.onRowAction(postgres.id, HistoryRowAction.TogglePin)
        runCurrent()
        assertNull(viewModel.state.openRow)
        assertEquals(listOf(listOf("useDebounce hook", "Postgres index")), viewModel.state.titles)
        assertEquals(
            FeatureHistoryRes.string.history_action_pin,
            viewModel.state.row(postgres.id)?.actions?.first()?.label,
        )
        assertEquals(listOf(postgres.id to true, postgres.id to false), repository.pins)
        // A pin is no news: no toast.
        assertNull(viewModel.state.toast)
    }

    @Test
    fun archiveHidesTheRowAndTheToastSaysArchivedFor1400Ms() = historyTest {
        val repository = FakeChatRepository()
        val postgres = repository.seed("Postgres index", updatedAt = Now - 1.hours)
        repository.seed("useDebounce hook")
        val viewModel = historyViewModel(repository)
        viewModel.onSwipeSettle(postgres.id, open = true)

        viewModel.onRowAction(postgres.id, HistoryRowAction.Archive)
        assertNull(viewModel.state.openRow)
        runCurrent()

        assertEquals(listOf(postgres.id), repository.archives)
        assertEquals(listOf(listOf("useDebounce hook")), viewModel.state.titles)
        assertEquals(1, viewModel.state.conversationCount)
        assertEquals(FeatureHistoryRes.string.history_toast_archived, viewModel.state.toast)
        advance(1399.milliseconds)
        assertEquals(FeatureHistoryRes.string.history_toast_archived, viewModel.state.toast)
        advance(1.milliseconds)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun deleteRemovesTheConversationAndTheToastSaysDeletedReplacingAnArchivedToast() = historyTest {
        val repository = FakeChatRepository()
        val postgres = repository.seed("Postgres index", updatedAt = Now - 1.hours)
        val debounce = repository.seed("useDebounce hook")
        val viewModel = historyViewModel(repository)
        viewModel.onRowAction(postgres.id, HistoryRowAction.Archive)
        runCurrent()
        advance(1000.milliseconds)

        viewModel.onRowAction(debounce.id, HistoryRowAction.Delete)
        runCurrent()

        assertEquals(listOf(debounce.id), repository.deletes)
        assertTrue(repository.conversations.value.none { it.id == debounce.id })
        assertTrue(viewModel.state.showsEmptyState)
        assertEquals(FeatureHistoryRes.string.history_toast_deleted, viewModel.state.toast)
        // A newer toast restarts the time.
        advance(1000.milliseconds)
        assertEquals(FeatureHistoryRes.string.history_toast_deleted, viewModel.state.toast)
        advance(400.milliseconds)
        assertNull(viewModel.state.toast)
    }

    @Test
    fun anActionThatChangesNothingOrFailsShowsNoToast() = historyTest {
        val repository = FakeChatRepository()
        val postgres = repository.seed("Postgres index")
        val viewModel = historyViewModel(repository)

        // Gone since the list was read: nothing to archive.
        repository.conversations.value = emptyList()
        viewModel.onRowAction(postgres.id, HistoryRowAction.Archive)
        runCurrent()
        assertNull(viewModel.state.toast)

        repository.seed("useDebounce hook").also { debounce ->
            repository.failure = IllegalStateException("disk full")
            viewModel.onRowAction(debounce.id, HistoryRowAction.Delete)
            runCurrent()
            assertNull(viewModel.state.toast)
            assertEquals(listOf(listOf("useDebounce hook")), viewModel.state.titles)
        }
    }

    // Navigation

    @Test
    fun aTapOnARowOpensItsConversationAsTextOverHistoryWhoseBackReturnsHereThenToVoice() = historyTest {
        val repository = FakeChatRepository()
        val postgres = repository.seed("Postgres index")
        val viewModel = historyViewModel(repository)

        viewModel.onRowClick(postgres.id)
        // A second tap before the thread shows opens it once.
        viewModel.onRowClick(postgres.id)
        assertEquals(listOf(VoiceEntry(), HistoryEntry, ChatEntry(conversationId = postgres.id)), viewModel.stack)

        // The thread's back, then History's.
        assertTrue(viewModel.navigateUp())
        assertEquals(listOf(VoiceEntry(), HistoryEntry), viewModel.stack)
        viewModel.onBack()
        assertEquals(listOf<NavKey>(VoiceEntry()), viewModel.stack)
    }

    @Test
    fun aTapOnARowPopsNothingWhateverIsUnderHistory() = historyTest {
        val repository = FakeChatRepository()
        val postgres = repository.seed("Postgres index")
        val underneath: List<List<NavKey>> = listOf(
            listOf(VoiceEntry(conversationId = 7)),
            listOf(VoiceEntry(), ChatEntry()),
            listOf(VoiceEntry(), ChatEntry(conversationId = 7)),
            emptyList(),
        )

        for (below in underneath) {
            val viewModel = historyViewModel(repository, backStack = below + HistoryEntry)

            viewModel.onRowClick(postgres.id)

            assertEquals(below + HistoryEntry + ChatEntry(conversationId = postgres.id), viewModel.stack)
        }
    }

    @Test
    fun aTapWhileARowIsOpenClosesItAndOpensNothing() = historyTest {
        val repository = FakeChatRepository()
        val postgres = repository.seed("Postgres index", updatedAt = Now - 1.hours)
        val debounce = repository.seed("useDebounce hook")
        val viewModel = historyViewModel(repository)
        val before = viewModel.stack

        viewModel.onSwipeSettle(postgres.id, open = true)
        viewModel.onRowClick(postgres.id)
        assertNull(viewModel.state.openRow)
        assertEquals(before, viewModel.stack)

        viewModel.onSwipeSettle(postgres.id, open = true)
        viewModel.onRowClick(debounce.id)
        assertNull(viewModel.state.openRow)
        assertEquals(before, viewModel.stack)

        viewModel.onRowClick(debounce.id)
        assertEquals(listOf(VoiceEntry(), HistoryEntry, ChatEntry(conversationId = debounce.id)), viewModel.stack)
    }

    @Test
    fun backReturnsToVoiceUnderneathAsItWas() = historyTest {
        val newSession = historyViewModel(backStack = listOf(VoiceEntry(), HistoryEntry))
        val inAConversation = historyViewModel(backStack = listOf(VoiceEntry(conversationId = 7), HistoryEntry))

        newSession.onBack()
        inAConversation.onBack()

        assertEquals(listOf<NavKey>(VoiceEntry()), newSession.stack)
        assertEquals(listOf<NavKey>(VoiceEntry(conversationId = 7)), inAConversation.stack)
    }

    @Test
    fun settingsOpensOverHistoryClosingAnOpenRowAndItsBackReturnsHereThenToVoice() = historyTest {
        val repository = FakeChatRepository()
        val postgres = repository.seed("Postgres index")
        val viewModel = historyViewModel(repository, backStack = listOf(VoiceEntry(conversationId = 7), HistoryEntry))
        viewModel.onSwipeSettle(postgres.id, open = true)

        viewModel.onOpenSettings()

        assertNull(viewModel.state.openRow)
        assertEquals(listOf(VoiceEntry(conversationId = 7), HistoryEntry, SettingsEntry), viewModel.stack)
        // Settings' back, then History's.
        assertTrue(viewModel.navigateUp())
        assertEquals(listOf(VoiceEntry(conversationId = 7), HistoryEntry), viewModel.stack)
        viewModel.onBack()
        assertEquals(listOf<NavKey>(VoiceEntry(conversationId = 7)), viewModel.stack)
    }

    @Test
    fun historyAloneGivesWayToANewVoiceSessionOnBack() = historyTest {
        val alone = historyViewModel(backStack = listOf(HistoryEntry))

        alone.onBack()

        // Never a dead end: Voice, the home screen, replaces History.
        assertEquals(listOf<NavKey>(VoiceEntry()), alone.stack)
    }

    @Test
    fun theCountFollowsTheConversationsAsTheyComeAndGo() = historyTest {
        val repository = FakeChatRepository()
        repository.seed("Postgres index")
        val viewModel = historyViewModel(repository)
        assertEquals("on this device · 1 conversation", viewModel.state.status)

        repository.seed("useDebounce hook", updatedAt = Now + 10.seconds)
        runCurrent()

        assertEquals("on this device · 2 conversations", viewModel.state.status)
        assertEquals(listOf(listOf("useDebounce hook", "Postgres index")), viewModel.state.titles)
    }
}
