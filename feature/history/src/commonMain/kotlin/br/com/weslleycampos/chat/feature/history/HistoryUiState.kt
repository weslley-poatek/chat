package br.com.weslleycampos.chat.feature.history

import androidx.compose.runtime.Immutable
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.HistorySection
import br.com.weslleycampos.chat.data.chat.models.groupHistory
import br.com.weslleycampos.chat.feature.history.resources.FeatureHistoryRes
import br.com.weslleycampos.chat.feature.history.resources.history_action_archive
import br.com.weslleycampos.chat.feature.history.resources.history_action_delete
import br.com.weslleycampos.chat.feature.history.resources.history_action_pin
import br.com.weslleycampos.chat.feature.history.resources.history_action_unpin
import br.com.weslleycampos.chat.feature.history.resources.history_section_earlier
import br.com.weslleycampos.chat.feature.history.resources.history_section_pinned
import br.com.weslleycampos.chat.feature.history.resources.history_section_today
import br.com.weslleycampos.chat.feature.history.resources.history_section_yesterday
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/**
 * Everything the History frame shows.
 *
 * @property query The search box's text, as typed; the list is searched with it trimmed.
 * @property groups The prototype's `hGroups`: "pinned", "today", "yesterday" and "earlier", each
 *   newest first, empty ones left out.
 * @property loaded The list has been read once, so an empty [groups] means nothing to list.
 * @property conversationCount How many conversations History lists without a query, or `null`
 *   until it is read: the status row.
 * @property openRow The row whose actions show, the prototype's `swipe` once released open; only
 *   one row is open at a time.
 * @property toast The toast's text while it shows: "archived" or "deleted".
 */
@Immutable
data class HistoryUiState(
    val query: String = "",
    val groups: List<HistoryGroupUi> = emptyList(),
    val loaded: Boolean = false,
    val conversationCount: Int? = null,
    val openRow: Long? = null,
    val toast: StringResource? = null,
)

/** One group of the list, under its lowercase label. */
@Immutable
data class HistoryGroupUi(
    val section: HistorySection,
    val rows: List<HistoryRowUi>,
) {
    val label: StringResource get() = section.label
}

/**
 * One row, the prototype's `hRow` (837–844) without folder and extra: "▲" when [pinned], the
 * [title], a dot in [palette]'s accent, [modelShortName] and [time].
 */
@Immutable
data class HistoryRowUi(
    val id: Long,
    val title: String,
    val pinned: Boolean,
    val modelShortName: String,
    val palette: ChatPalette,
    val time: String,
)

/** What a row's swipe reveals, in the prototype's order. */
enum class HistoryRowAction {
    TogglePin,
    Archive,
    Delete,
}

/** One action behind a row and its label, which touch and screen readers both use. */
@Immutable
data class HistoryRowActionUi(val action: HistoryRowAction, val label: StringResource)

/** The row's actions: "pin" ("unpin" when pinned), "archive", "delete". */
val HistoryRowUi.actions: List<HistoryRowActionUi>
    get() = listOf(
        HistoryRowActionUi(
            action = HistoryRowAction.TogglePin,
            label = if (pinned) {
                FeatureHistoryRes.string.history_action_unpin
            } else {
                FeatureHistoryRes.string.history_action_pin
            },
        ),
        HistoryRowActionUi(action = HistoryRowAction.Archive, label = FeatureHistoryRes.string.history_action_archive),
        HistoryRowActionUi(action = HistoryRowAction.Delete, label = FeatureHistoryRes.string.history_action_delete),
    )

/** The group label: the prototype's `Pinned` / `Today` / … under `text-transform:lowercase`. */
val HistorySection.label: StringResource
    get() = when (this) {
        HistorySection.PINNED -> FeatureHistoryRes.string.history_section_pinned
        HistorySection.TODAY -> FeatureHistoryRes.string.history_section_today
        HistorySection.YESTERDAY -> FeatureHistoryRes.string.history_section_yesterday
        HistorySection.EARLIER -> FeatureHistoryRes.string.history_section_earlier
    }

/** The status row's text, from the count. */
val HistoryUiState.status: String get() = historyStatus(conversationCount)

/** The search box has text, so it shows "×". */
val HistoryUiState.showsClearQuery: Boolean get() = query.isNotEmpty()

/** The trimmed query when it matched nothing, the prototype's `hEmpty`: `no matches for “<query>”`. */
val HistoryUiState.noMatchesFor: String?
    get() = query.trim().takeIf { loaded && groups.isEmpty() && it.isNotEmpty() }

/** First launch, or every conversation archived or deleted: nothing to list and no query. */
val HistoryUiState.showsEmptyState: Boolean get() = loaded && groups.isEmpty() && query.isBlank()

/** "swipe left on a row to pin, archive or delete", under the rows it is about. */
val HistoryUiState.showsFooterHint: Boolean get() = groups.isNotEmpty()

/** The row with this [id], wherever it is grouped. */
fun HistoryUiState.row(id: Long): HistoryRowUi? =
    groups.firstNotNullOfOrNull { group -> group.rows.firstOrNull { it.id == id } }

/**
 * History's groups for [conversations], as `ChatRepository.observeHistory` lists them, with their
 * rows: grouped by [groupHistory] against [now] in [timeZone], each row's time read the same way.
 */
fun historyGroups(conversations: List<Conversation>, now: Instant, timeZone: TimeZone): List<HistoryGroupUi> =
    groupHistory(conversations = conversations, now = now, timeZone = timeZone).map { group ->
        HistoryGroupUi(
            section = group.section,
            rows = group.conversations.map { it.toRowUi(now = now, timeZone = timeZone) },
        )
    }

/** A conversation as its row shows it. */
fun Conversation.toRowUi(now: Instant, timeZone: TimeZone): HistoryRowUi = HistoryRowUi(
    id = id,
    title = title,
    pinned = pinned,
    modelShortName = model.shortName,
    palette = model.palette,
    time = historyRowTime(updatedAt = updatedAt, now = now, timeZone = timeZone),
)
