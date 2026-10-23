package br.com.weslleycampos.chat.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.data.chat.ChatRepository
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.feature.history.resources.FeatureHistoryRes
import br.com.weslleycampos.chat.feature.history.resources.history_toast_archived
import br.com.weslleycampos.chat.feature.history.resources.history_toast_deleted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration

/**
 * The History frame's logic: the prototype's `hQuery` filter, `hGroups`, the swipe state and the
 * row actions `pin`, `archive` and `del` (833–845), on [ChatRepository]. The list is
 * [ChatRepository.observeHistory] for the trimmed query, grouped again whenever it changes and
 * whenever the local date does, since nothing in the database changes at midnight.
 *
 * Navigation: back returns to the chat underneath, as it was; a row replaces that chat with its own
 * conversation, so History never piles up on the back stack.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel(binds = [HistoryViewModel::class])
class HistoryViewModel(
    val navigator: Navigator,
    private val repository: ChatRepository,
    private val times: HistoryTimes,
) : ViewModel(), Navigator by navigator {
    val uiState: StateFlow<HistoryUiState>
        field = MutableStateFlow(HistoryUiState())

    /** The search box's text. Local data, so every keystroke searches: there is no debounce. */
    private val query = MutableStateFlow("")

    /** The prototype's `toastMsg`: a new toast replaces the shown one and restarts its time. */
    private val toast = TimedValue<StringResource>(viewModelScope, ChatToastDefaults.Duration) { message ->
        uiState.update { it.copy(toast = message) }
    }

    init {
        viewModelScope.launch {
            repository.observeConversationCount().collect { count ->
                uiState.update { it.copy(conversationCount = count) }
            }
        }
        viewModelScope.launch {
            val conversations = query
                .map { it.trim() }
                .distinctUntilChanged()
                .flatMapLatest { repository.observeHistory(it) }
            combine(conversations, times.days()) { list, _ -> list }.collect(::show)
        }
    }

    /** The search box: the list follows at once, and "×" shows while there is text. */
    fun onQueryChange(text: String) {
        uiState.update { it.copy(query = text) }
        query.value = text
    }

    /** "×", the prototype's `clearHQuery`: back to every conversation. */
    fun onClearQuery() = onQueryChange("")

    /**
     * The back chevron, as system back and the iOS edge swipe: the chat underneath, unchanged. If
     * History were ever the only screen, a new chat replaces it instead of leaving nothing.
     */
    fun onBack() {
        if (!navigateUp()) navigate(ChatEntry()) { popUpTo<HistoryEntry> { inclusive = true } }
    }

    /**
     * A tap on a row, the prototype's `open`: with a row open it only closes that row (a tap on
     * the open row closes it, and one on another row does not navigate away under the actions).
     * Otherwise the conversation opens in place of the chat underneath: back from it then leaves
     * the app, as from any chat, rather than coming back here.
     */
    fun onRowClick(conversationId: Long) {
        if (uiState.value.openRow != null) {
            onCloseRow()
            return
        }
        navigate(ChatEntry(conversationId)) { popUpTo<ChatEntry> { inclusive = true } }
    }

    /** A drag starts on a row: any other open row closes, so one row at most is open. */
    fun onSwipeStart(conversationId: Long) = uiState.update {
        if (it.openRow != null && it.openRow != conversationId) it.copy(openRow = null) else it
    }

    /** A drag is released: the row is open past half of its actions' width, else closed. */
    fun onSwipeSettle(conversationId: Long, open: Boolean) = uiState.update {
        when {
            open -> it.copy(openRow = conversationId)
            it.openRow == conversationId -> it.copy(openRow = null)
            else -> it
        }
    }

    /** Closes the open row, if any. */
    fun onCloseRow() = uiState.update { it.copy(openRow = null) }

    /**
     * An action behind a row, tapped or picked from a screen reader's actions; the row closes.
     * - "pin" / "unpin": the row moves to "pinned" or back to its day, with no toast.
     * - "archive": the row leaves the list, and the toast says "archived" if it was archived.
     * - "delete": the conversation and its messages are gone, and the toast says "deleted" if they were.
     */
    fun onRowAction(conversationId: Long, action: HistoryRowAction) {
        val row = uiState.value.row(conversationId)
        onCloseRow()
        when (action) {
            HistoryRowAction.TogglePin -> if (row != null) act { repository.setPinned(conversationId, !row.pinned) }
            HistoryRowAction.Archive ->
                act(done = FeatureHistoryRes.string.history_toast_archived) { repository.archive(conversationId) }
            HistoryRowAction.Delete ->
                act(done = FeatureHistoryRes.string.history_toast_deleted) { repository.delete(conversationId) }
        }
    }

    /** Runs a row action; the toast says [done] only when it changed something, never for a row already gone. */
    private fun act(done: StringResource? = null, action: suspend () -> Boolean) {
        viewModelScope.launch {
            val changed = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // The prototype has no failure to show; the row stays as the database has it.
                false
            }
            if (changed && done != null) toast.show(done)
        }
    }

    private fun show(conversations: List<Conversation>) {
        val groups = historyGroups(conversations = conversations, now = times.now(), timeZone = times.timeZone())
        uiState.update { state ->
            state.copy(
                groups = groups,
                loaded = true,
                // A row that left the list (archived, deleted, filtered out) is no longer open.
                openRow = state.openRow?.takeIf { id -> conversations.any { it.id == id } },
            )
        }
    }
}

/**
 * A value shown for [duration] and then cleared, through [onChange]: showing another one restarts
 * the time. The chat's toast works the same way.
 */
internal class TimedValue<T : Any>(
    private val scope: CoroutineScope,
    private val duration: Duration,
    private val onChange: (T?) -> Unit,
) {
    private var job: Job? = null

    fun show(value: T) {
        job?.cancel()
        onChange(value)
        job = scope.launch {
            delay(duration)
            onChange(null)
        }
    }
}
