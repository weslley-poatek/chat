package br.com.weslleycampos.chat.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatIconButton
import br.com.weslleycampos.chat.core.ui.components.ChatToast
import br.com.weslleycampos.chat.core.ui.icons.ChatIcons
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.feature.history.resources.FeatureHistoryRes
import br.com.weslleycampos.chat.feature.history.resources.history_back
import br.com.weslleycampos.chat.feature.history.resources.history_empty
import br.com.weslleycampos.chat.feature.history.resources.history_footer
import br.com.weslleycampos.chat.feature.history.resources.history_memory
import br.com.weslleycampos.chat.feature.history.resources.history_no_matches
import br.com.weslleycampos.chat.feature.history.resources.history_search_clear
import br.com.weslleycampos.chat.feature.history.resources.history_search_placeholder
import br.com.weslleycampos.chat.feature.history.resources.history_settings
import br.com.weslleycampos.chat.feature.history.resources.history_title
import br.com.weslleycampos.chat.feature.history.resources.history_toast_archived
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The status row's 7 px ring and its 1.5 px stroke, the prototype's "local only" indicator. */
private val StatusRingSize = 7.dp
private val StatusRingStroke = 1.5.dp

/** The search field: 40 tall, mono 13. */
private val SearchFieldHeight = 40.dp
private val SearchFontSize = 13.sp

/** The prototype's toast sits 84 px over the bottom inset. */
private val ToastBottomOffset = 84.dp

/** The prototype's "×" clear. */
private const val ClearMark = "×"

/**
 * The History frame (frames.md §2.3): the bar, the status row, the search box and the grouped,
 * swipeable list with its footer hint, inside the safe area. The list scrolls under the bottom inset
 * and above the keyboard; the toast floats over its bottom.
 *
 * History shows no conversation, so it wears the root's accent; each row's dot is its own model's.
 */
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onBack: () -> Unit,
    onMemoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onRowClick: (Long) -> Unit,
    onSwipeStart: (Long) -> Unit,
    onSwipeSettle: (Long, Boolean) -> Unit,
    onRowAction: (Long, HistoryRowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Below the keyboard while it shows, else the navigation bar or home indicator.
    val bottomInset = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = ChatTheme.colors.surface.primary)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        HistoryTopBar(onBack = onBack, onMemoryClick = onMemoryClick, onSettingsClick = onSettingsClick)
        StatusRow(text = uiState.status)
        SearchField(
            query = uiState.query,
            showsClear = uiState.showsClearQuery,
            onQueryChange = onQueryChange,
            onClearQuery = onClearQuery,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset + ChatTheme.spacing.large),
            ) {
                for (group in uiState.groups) {
                    item(key = "section-${group.section.name}", contentType = "label") {
                        HistoryGroupLabel(text = stringResource(group.label))
                    }
                    items(items = group.rows, key = { it.id }, contentType = { "row" }) { row ->
                        HistoryRow(
                            row = row,
                            open = uiState.openRow == row.id,
                            onClick = { onRowClick(row.id) },
                            onSwipeStart = { onSwipeStart(row.id) },
                            onSwipeSettle = { open -> onSwipeSettle(row.id, open) },
                            onAction = { action -> onRowAction(row.id, action) },
                        )
                    }
                }
                uiState.noMatchesFor?.let { query ->
                    item(key = "no-matches") {
                        HistoryMessage(text = stringResource(FeatureHistoryRes.string.history_no_matches, query))
                    }
                }
                if (uiState.showsEmptyState) {
                    item(key = "empty") {
                        HistoryMessage(text = stringResource(FeatureHistoryRes.string.history_empty))
                    }
                }
                if (uiState.showsFooterHint) {
                    item(key = "footer") {
                        HistoryFooterHint(text = stringResource(FeatureHistoryRes.string.history_footer))
                    }
                }
            }
            ChatToast(
                message = uiState.toast?.let { stringResource(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + ToastBottomOffset),
            )
        }
    }
}

/** Back, the title, Memory and Settings (frame lines 256–261), with no hairline under it. */
@Composable
private fun HistoryTopBar(onBack: () -> Unit, onMemoryClick: () -> Unit, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChatTheme.sizes.xxxLarge)
            .padding(horizontal = ChatTheme.spacing.xSmall),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.xxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChatIconButton(onClick = onBack, contentDescription = stringResource(FeatureHistoryRes.string.history_back)) {
            Icon(painter = painterResource(ChatIcons.Back), contentDescription = null)
        }
        Text(
            text = stringResource(FeatureHistoryRes.string.history_title),
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = ChatTheme.typography.headlineLarge,
            color = ChatTheme.colors.text.primary,
        )
        ChatIconButton(
            onClick = onMemoryClick,
            contentDescription = stringResource(FeatureHistoryRes.string.history_memory),
        ) {
            Icon(painter = painterResource(ChatIcons.Memory), contentDescription = null)
        }
        ChatIconButton(
            onClick = onSettingsClick,
            contentDescription = stringResource(FeatureHistoryRes.string.history_settings),
        ) {
            Icon(painter = painterResource(ChatIcons.Settings), contentDescription = null)
        }
    }
}

/** The hollow ring and "on this device · 12 conversations", in place of the sync state. */
@Composable
private fun StatusRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ChatTheme.spacing.xxLarge,
                end = ChatTheme.spacing.xxLarge,
                bottom = ChatTheme.spacing.medium,
            ),
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(StatusRingSize)
                .border(
                    width = StatusRingStroke,
                    color = ChatTheme.colors.text.tertiary,
                    shape = ChatTheme.shapes.pill,
                ),
        )
        Text(text = text, style = ChatTheme.typography.labelSmall, color = ChatTheme.colors.text.tertiary)
    }
}

/**
 * The search box (frame lines 265–271): `--bg2`, the search icon, mono 13 and "×" while it has
 * text. "×" clears without taking the focus, so the keyboard stays; Search on the keyboard closes it.
 */
@Composable
private fun SearchField(query: String, showsClear: Boolean, onQueryChange: (String) -> Unit, onClearQuery: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val shape = ChatTheme.shapes.control
    val textStyle = ChatTheme.typography.input.copy(fontSize = SearchFontSize, color = ChatTheme.colors.text.primary)
    val placeholder = stringResource(FeatureHistoryRes.string.history_search_placeholder)
    val clearLabel = stringResource(FeatureHistoryRes.string.history_search_clear)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ChatTheme.spacing.large, end = ChatTheme.spacing.large, bottom = ChatTheme.spacing.small)
            .height(SearchFieldHeight),
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        singleLine = true,
        cursorBrush = SolidColor(ChatTheme.colors.text.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .background(color = ChatTheme.colors.surface.secondary, shape = shape)
                    .border(width = 1.dp, color = ChatTheme.colors.border.primary, shape = shape)
                    .padding(
                        start = ChatTheme.spacing.medium,
                        end = if (showsClear) 0.dp else ChatTheme.spacing.medium,
                    ),
                horizontalArrangement = Arrangement.spacedBy(ChatTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(ChatIcons.Search),
                    contentDescription = null,
                    tint = ChatTheme.colors.text.tertiary,
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = ChatTheme.colors.text.tertiary,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
                if (showsClear) {
                    Box(
                        modifier = Modifier
                            .width(ChatTheme.sizes.xxLarge)
                            .fillMaxHeight()
                            .clickable(role = Role.Button, onClick = onClearQuery)
                            .semantics { contentDescription = clearLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = ClearMark, style = textStyle, color = ChatTheme.colors.text.tertiary)
                    }
                }
            }
        },
    )
}

/** The previews' "now": 14:02 UTC on the prototype's "today", Sep 14. */
private val PreviewNow = Instant.parse("2026-09-14T14:02:00Z")
private val PreviewZone = TimeZone.UTC

/** The prototype's `HISTORY` (557–568), without folders or the "1 image" extra, at its times. */
private val PreviewConversations: List<Conversation> = listOf(
    previewConversation(
        id = 1,
        title = "useDebounce hook with leading edge",
        modelId = "sonnet",
        updatedAt = PreviewNow,
        pinned = true,
    ),
    previewConversation(
        id = 2,
        title = "Postgres index on partial JSONB column",
        modelId = "gpt5",
        updatedAt = PreviewNow - 2.hours - 14.minutes,
    ),
    previewConversation(
        id = 3,
        title = "Rewrite CI matrix for pnpm workspaces",
        modelId = "sonnet",
        updatedAt = PreviewNow - 4.hours - 47.minutes,
    ),
    previewConversation(
        id = 4,
        title = "SSE vs WebSocket for streaming chat",
        modelId = "gemini",
        updatedAt = PreviewNow - 15.hours - 31.minutes,
        comparingWith = "gpt5",
    ),
    previewConversation(
        id = 5,
        title = "Explain this flamegraph",
        modelId = "opus",
        updatedAt = PreviewNow - 20.hours - 57.minutes,
    ),
    previewConversation(
        id = 6,
        title = "Regex for semver ranges incl. prerelease",
        modelId = "deepseek",
        updatedAt = PreviewNow - 24.hours - 42.minutes,
    ),
    previewConversation(
        id = 7,
        title = "RFC: memory retention policy",
        modelId = "sonnet",
        updatedAt = PreviewNow - 2.days - 2.hours - 42.minutes,
        pinned = true,
    ),
    previewConversation(
        id = 8,
        title = "Rust ownership in async closures",
        modelId = "o3",
        updatedAt = PreviewNow - 2.days - 3.hours,
    ),
    previewConversation(
        id = 9,
        title = "Migrate ESLint config to flat format",
        modelId = "gpt5",
        updatedAt = PreviewNow - 2.days - 5.hours,
    ),
    previewConversation(
        id = 10,
        title = "Terraform module for S3 + CloudFront",
        modelId = "llama",
        updatedAt = PreviewNow - 3.days - 1.hours,
    ),
)

private fun previewConversation(
    id: Long,
    title: String,
    modelId: String,
    updatedAt: Instant,
    pinned: Boolean = false,
    comparingWith: String? = null,
) = Conversation(
    id = id,
    title = title,
    model = ModelCatalogue[modelId],
    createdAt = updatedAt,
    updatedAt = updatedAt,
    pinned = pinned,
    compareOn = comparingWith != null,
    compareWith = comparingWith?.let { ModelCatalogue[it] },
)

private fun previewState(
    query: String = "",
    conversations: List<Conversation> = PreviewConversations,
    openRow: Long? = null,
) = HistoryUiState(
    query = query,
    groups = historyGroups(
        conversations = conversations.filter { conversation ->
            query.isBlank() ||
                conversation.title.contains(query.trim(), ignoreCase = true) ||
                conversation.model.shortName.contains(query.trim(), ignoreCase = true)
        },
        now = PreviewNow,
        timeZone = PreviewZone,
    ),
    loaded = true,
    conversationCount = conversations.size,
    openRow = openRow,
)

data class HistoryPreviewCase(val name: String, val uiState: HistoryUiState, val darkTheme: Boolean) {
    val displayName: String get() = "$name · ${if (darkTheme) "Dark" else "Light"}"
}

/** Every state of the History frame, in light and dark. */
class HistoryScreenPreviewParameterProvider : PreviewParameterProvider<HistoryPreviewCase> {
    private val states = listOf(
        "Grouped" to previewState(),
        "Filtered" to previewState(query = "gpt"),
        "No matches" to previewState(query = "kubernetes"),
        "Empty" to previewState(conversations = emptyList()),
        "Swiped open" to previewState(openRow = 2),
        "Swiped open · pinned" to previewState(openRow = 1),
        "Toast" to previewState(conversations = PreviewConversations.filterNot { it.id == 3L })
            .copy(toast = FeatureHistoryRes.string.history_toast_archived),
    )

    override val values: Sequence<HistoryPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for ((name, state) in states) {
                yield(HistoryPreviewCase(name = name, uiState = state, darkTheme = dark))
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).displayName
}

@Preview(name = "History", widthDp = 402, heightDp = 874)
@Composable
private fun HistoryScreenPreview(
    @PreviewParameter(HistoryScreenPreviewParameterProvider::class) case: HistoryPreviewCase,
) {
    ChatTheme(darkTheme = case.darkTheme) {
        HistoryScreen(
            uiState = case.uiState,
            onBack = {},
            onMemoryClick = {},
            onSettingsClick = {},
            onQueryChange = {},
            onClearQuery = {},
            onRowClick = {},
            onSwipeStart = {},
            onSwipeSettle = { _, _ -> },
            onRowAction = { _, _ -> },
        )
    }
}
