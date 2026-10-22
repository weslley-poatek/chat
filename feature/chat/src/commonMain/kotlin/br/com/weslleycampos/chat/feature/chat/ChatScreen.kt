package br.com.weslleycampos.chat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import br.com.weslleycampos.chat.core.ui.ChatTheme
import br.com.weslleycampos.chat.core.ui.components.ChatToast
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_send_failed
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * The Chat frame (frames.md §1.2–1.7): the top bar, the thread or, for a new chat, the empty
 * state, and the composer. It sits inside the safe area and above the keyboard, which shortens the
 * thread rather than covering it. The toast floats over the thread's bottom edge.
 *
 * The accent is the [ChatUiState.model]'s: wrap the screen in `ChatTheme(palette = uiState.model.palette)`.
 * The Model picker sheet the chip opens is [br.com.weslleycampos.chat.feature.chat.picker.ModelPickerSheet],
 * shown next to the screen.
 */
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onHistoryClick: () -> Unit,
    onModelClick: () -> Unit,
    onNewChat: () -> Unit,
    onOpenRecent: (Long) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onCopyCode: (BlockKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = ChatTheme.colors.surface.primary)
            .safeDrawingPadding(),
    ) {
        ChatTopBar(
            chipLabel = uiState.chipLabel,
            onHistoryClick = onHistoryClick,
            onModelClick = onModelClick,
            onNewChat = onNewChat,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (uiState.showsEmptyState) {
                ChatEmptyState(
                    model = uiState.model,
                    persona = uiState.persona,
                    recent = uiState.recent,
                    onOpenRecent = onOpenRecent,
                )
            } else {
                ChatThread(messages = uiState.messages, copiedBlock = uiState.copiedBlock, onCopyCode = onCopyCode)
            }
            ChatToast(
                message = uiState.toast?.let { stringResource(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // The prototype's toast sits 84 px over the bottom edge: about 24 over the composer.
                    .padding(bottom = ChatTheme.spacing.xxxLarge + ChatTheme.spacing.xxxSmall),
            )
        }
        ChatComposer(
            draft = uiState.draft,
            draftRows = uiState.draftRows,
            modelShortName = uiState.model.shortName,
            canSend = uiState.canSend,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onAttachClick = onAttachClick,
            onVoiceClick = onVoiceClick,
        )
    }
}

private const val PreviewConversationId = 1L
private const val PreviewUserTime = "14:01"
private const val PreviewReplyTime = "14:02"
private val PreviewLatency = 800.milliseconds
private val PreviewGemini = ModelCatalogue["gemini"]
private val PreviewReviewer = PersonaPresets.personas.first()

private val PreviewRecent = listOf(
    RecentConversationUi(id = 1, title = "useDebounce hook with leading edge", time = "14:02"),
    RecentConversationUi(id = 2, title = "Postgres index on partial JSONB column", time = "11:48"),
    RecentConversationUi(id = 3, title = "Rewrite CI matrix for pnpm workspaces", time = "Sep 13"),
)

private const val PreviewDebounceCode = "import { useEffect, useState } from \"react\";\n" +
    "\n" +
    "export function useDebounce<T>(value: T, delay = 300): T {\n" +
    "  const [debounced, setDebounced] = useState(value);\n" +
    "\n" +
    "  useEffect(() => {\n" +
    "    const id = setTimeout(() => setDebounced(value), delay);\n" +
    "    return () => clearTimeout(id);\n" +
    "  }, [value, delay]);\n" +
    "\n" +
    "  return debounced;\n" +
    "}"

/** The prototype's first canned reply. */
private val PreviewReplyBlocks = listOf(
    Block.Paragraph("Two likely causes, in order of probability:"),
    Block.NumberedList(
        listOf(
            "The effect closes over a stale value because delay is missing from the dependency array.",
            "The timeout id is reassigned before the previous one is cleared, so cleanup clears the wrong timer.",
        ),
    ),
    Block.Paragraph("Minimal fix:"),
    Block.Code(
        language = "ts",
        text = "useEffect(() => {\n" +
            "  const id = setTimeout(() => setDebounced(value), delay);\n" +
            "  return () => clearTimeout(id);\n" +
            "}, [value, delay]);",
    ),
    Block.Paragraph("If it still reads stale, log value inside the callback and check the render count."),
)

private fun previewUser(id: Long, text: String) = MessageUi.User(id = id, time = PreviewUserTime, text = text)

private fun previewReply(
    id: Long,
    status: ReplyStatus,
    blocks: List<Block>,
    model: ChatModel = ModelCatalogue.default,
    persona: Persona? = null,
    latency: Duration? = PreviewLatency.takeIf { status == ReplyStatus.DONE },
) = Message.Assistant(
    id = id,
    conversationId = PreviewConversationId,
    model = model,
    persona = persona,
    blocks = blocks,
    status = status,
    latency = latency,
    createdAt = Instant.fromEpochMilliseconds(0),
).toUi(time = PreviewReplyTime)

private fun previewThread(
    vararg messages: MessageUi,
    choice: ModelChoice = ModelChoice(ModelCatalogue.default),
) = ChatUiState(
    conversationId = PreviewConversationId,
    choice = choice,
    messages = messages.toList(),
    replyInProgress = (messages.lastOrNull() as? MessageUi.Assistant)?.let { it.thinking || it.hasCaret } == true,
)

private val MessageUi.Assistant.hasCaret: Boolean
    get() = blocks.any { block ->
        when (block) {
            is BlockUi.Paragraph -> block.caret
            is BlockUi.Code -> block.caret
            is BlockUi.NumberedList -> false
        }
    }

private const val PreviewMultiLineDraft = "Add a leading option.\nKeep it generic,\nand cancel on unmount."
private const val PreviewQuestion = "My debounced value is always one render behind. Why?"

data class ChatPreviewCase(val name: String, val uiState: ChatUiState, val darkTheme: Boolean) {
    val displayName: String get() = "$name · ${if (darkTheme) "Dark" else "Light"}"
}

/** Every state of the Chat frame, in light and dark. */
class ChatScreenPreviewParameterProvider : PreviewParameterProvider<ChatPreviewCase> {
    private val question = previewUser(id = 1, text = PreviewQuestion)
    private val done =
        previewThread(question, previewReply(id = 2, status = ReplyStatus.DONE, blocks = PreviewReplyBlocks))
    private val streaming = previewThread(
        question,
        previewReply(id = 2, status = ReplyStatus.STREAMING, blocks = PreviewReplyBlocks.take(1)),
    )
    private val states = listOf(
        "Empty" to ChatUiState(),
        "Empty · recent" to ChatUiState(recent = PreviewRecent),
        "Empty · persona" to ChatUiState(choice = ModelChoice(PreviewReviewer), recent = PreviewRecent),
        "Thinking" to previewThread(
            question,
            previewReply(id = 2, status = ReplyStatus.THINKING, blocks = emptyList()),
        ),
        "Streaming" to streaming,
        "Done" to done,
        "Done · persona" to previewThread(
            question,
            previewReply(id = 2, status = ReplyStatus.DONE, blocks = PreviewReplyBlocks, persona = PreviewReviewer),
            choice = ModelChoice(PreviewReviewer),
        ),
        "Interrupted" to previewThread(
            question,
            previewReply(id = 2, status = ReplyStatus.INTERRUPTED, blocks = PreviewReplyBlocks.take(2)),
        ),
        "Long code block" to previewThread(
            previewUser(id = 1, text = "Write a useDebounce hook in TS. Generic, cancel on unmount."),
            previewReply(
                id = 2,
                status = ReplyStatus.STREAMING,
                blocks = listOf(
                    Block.Paragraph("Generic over the value type. The pending timer is cleared on unmount."),
                    Block.Code(language = "ts", text = PreviewDebounceCode),
                ),
            ),
        ),
        "List" to previewThread(
            question,
            previewReply(id = 2, status = ReplyStatus.DONE, blocks = PreviewReplyBlocks.take(2)),
        ),
        "Composer · multi-line draft" to done.copy(draft = PreviewMultiLineDraft),
        "Send disabled" to streaming.copy(draft = "Add a leading option"),
        "Toast" to ChatUiState(
            recent = PreviewRecent,
            draft = "Add a leading option",
            toast = FeatureChatRes.string.chat_toast_send_failed,
        ),
        "Copied · Gemini" to previewThread(
            question,
            previewReply(id = 2, status = ReplyStatus.DONE, blocks = PreviewReplyBlocks, model = PreviewGemini),
            choice = ModelChoice(PreviewGemini),
        ).copy(copiedBlock = BlockKey(messageId = 2, index = 3)),
    )

    override val values: Sequence<ChatPreviewCase> = sequence {
        for (dark in listOf(false, true)) {
            for ((name, state) in states) {
                yield(ChatPreviewCase(name = name, uiState = state, darkTheme = dark))
            }
        }
    }

    override fun getDisplayName(index: Int): String = values.elementAt(index).displayName
}

@Preview(name = "Chat", widthDp = 402, heightDp = 874)
@Composable
private fun ChatScreenPreview(
    @PreviewParameter(ChatScreenPreviewParameterProvider::class) case: ChatPreviewCase,
) {
    ChatTheme(palette = case.uiState.model.palette, darkTheme = case.darkTheme) {
        ChatScreen(
            uiState = case.uiState,
            onHistoryClick = {},
            onModelClick = {},
            onNewChat = {},
            onOpenRecent = {},
            onDraftChange = {},
            onSend = {},
            onAttachClick = {},
            onVoiceClick = {},
            onCopyCode = {},
        )
    }
}
