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
import br.com.weslleycampos.chat.data.chat.models.CompareSide
import br.com.weslleycampos.chat.data.chat.models.ForkOrigin
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_continuing
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_forked
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_send_failed
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * The Chat frame (frames.md §1.2–1.7): the top bar, a fork's banner, the thread or, for a new chat,
 * the empty state, and the composer. It sits inside the safe area and above the keyboard, which
 * shortens the thread rather than covering it. The toast floats over the thread's bottom edge. A
 * tap on a message, its action row, the read-aloud bar and a compare card's "continue with this →"
 * call [messageActions]. It is how a conversation reads as text, over History or Voice: [onBack]
 * returns there, and the composer's mic, [onVoiceClick], goes on in voice.
 *
 * The accent is the [ChatUiState.model]'s, the primary model's while comparing: wrap the screen in
 * `ChatTheme(palette = uiState.model.palette)`. Each compare card wears its own model's.
 * The Model picker sheet the chip opens is [br.com.weslleycampos.chat.feature.chat.picker.ModelPickerSheet],
 * shown next to the screen.
 */
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onBack: () -> Unit,
    onModelClick: () -> Unit,
    onOpenRecent: (Long) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    onCopyCode: (BlockKey) -> Unit,
    onDismissForkBanner: () -> Unit,
    messageActions: MessageActionCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = ChatTheme.colors.surface.primary)
            .safeDrawingPadding(),
    ) {
        ChatTopBar(chipLabel = uiState.chipLabel, onBack = onBack, onModelClick = onModelClick)
        uiState.forkBanner?.let { banner -> ForkBanner(text = banner, onDismiss = onDismissForkBanner) }
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
                ChatThread(
                    messages = uiState.messages,
                    copiedBlock = uiState.copiedBlock,
                    onCopyCode = onCopyCode,
                    selectedMessage = uiState.selectedMessage,
                    copiedMessage = uiState.copiedMessage,
                    readAloud = uiState.readAloud,
                    actions = messageActions,
                )
            }
            ChatToast(
                message = uiState.toast?.let { toastText(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // The prototype's toast sits 84 px over the bottom edge: about 24 over the composer.
                    .padding(bottom = ChatTheme.spacing.xxxLarge + ChatTheme.spacing.xxxSmall),
            )
        }
        ChatComposer(
            draft = uiState.draft,
            draftRows = uiState.draftRows,
            placeholder = uiState.composerPlaceholder,
            canSend = uiState.canSend,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onVoiceClick = onVoiceClick,
        )
    }
}

/**
 * The toast's text, with its [ChatToastUi.args] in the placeholders: none, one ("continuing with
 * gpt-5") or two ("comparing sonnet-4.5 ⇄ gpt-5"), which is as many as a chat toast has.
 */
@Composable
private fun toastText(toast: ChatToastUi): String = when (toast.args.size) {
    0 -> stringResource(toast.message)
    1 -> stringResource(toast.message, toast.args[0])
    else -> stringResource(toast.message, toast.args[0], toast.args[1])
}

private const val PreviewConversationId = 1L
private const val PreviewUserTime = "14:01"
private const val PreviewReplyTime = "14:02"
private val PreviewLatency = 800.milliseconds
private val PreviewGemini = ModelCatalogue["gemini-2.5-pro"]
private val PreviewGpt5 = ModelCatalogue["gpt-5"]
private val PreviewCompare = ModelChoice(ModelCatalogue.default, compareOn = true, compareWith = PreviewGpt5)
private val PreviewSecondLatency = 1900.milliseconds
private val PreviewReviewer = PersonaPresets.personas.first()
private val PreviewFork = ForkOrigin(sourceTitle = "useDebounce hook with leading edge", messageNumber = 2)

/** 42 % of the bar, "0:07 / 0:17", and 66 %, "0:11 / 0:17". */
private const val PreviewReadingTicks = 70
private const val PreviewPausedTicks = 110

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

/** The prototype's first alternative reply, `REPLY_ALT[0]`: the compared model's card. */
private val PreviewAltBlocks = listOf(
    Block.Paragraph(
        "Most likely a stale closure. Include every value the effect reads in its dependency array, " +
            "and return the cleanup:",
    ),
    Block.Code(
        language = "ts",
        text = "useEffect(() => {\n" +
            "  const t = setTimeout(() => setDebounced(value), delay);\n" +
            "  return () => clearTimeout(t);\n" +
            "}, [value, delay]);",
    ),
)

private fun previewUser(id: Long, text: String) = MessageUi.User(id = id, time = PreviewUserTime, text = text)

/** A pair of sonnet's [first] blocks and gpt-5's [second], each with its status. */
private fun previewPair(
    first: Pair<ReplyStatus, List<Block>>,
    second: Pair<ReplyStatus, List<Block>>,
) = Message.ComparePair(
    id = 2,
    conversationId = PreviewConversationId,
    sides = listOf(
        CompareSide(
            id = 2,
            model = ModelCatalogue.default,
            blocks = first.second,
            status = first.first,
            latency = PreviewLatency.takeIf { first.first == ReplyStatus.DONE },
        ),
        CompareSide(
            id = 3,
            model = PreviewGpt5,
            blocks = second.second,
            status = second.first,
            latency = PreviewSecondLatency.takeIf { second.first == ReplyStatus.DONE },
        ),
    ),
    createdAt = Instant.fromEpochMilliseconds(0),
).toUi(time = PreviewReplyTime)

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
    replyInProgress = when (val last = messages.lastOrNull()) {
        is MessageUi.Assistant -> last.inProgress
        is MessageUi.ComparePair -> last.inProgress
        is MessageUi.User, null -> false
    },
)

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
    private val comparedDone = previewPair(
        first = ReplyStatus.DONE to PreviewReplyBlocks,
        second = ReplyStatus.DONE to PreviewAltBlocks,
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
            toast = ChatToastUi(FeatureChatRes.string.chat_toast_send_failed),
        ),
        "Copied · Gemini" to previewThread(
            question,
            previewReply(id = 2, status = ReplyStatus.DONE, blocks = PreviewReplyBlocks, model = PreviewGemini),
            choice = ModelChoice(PreviewGemini),
        ).copy(copiedBlock = BlockKey(messageId = 2, index = 3)),
        "Selected · question" to done.copy(selectedMessage = 1),
        "Selected · reply" to done.copy(selectedMessage = 2),
        "Copied · reply" to done.copy(selectedMessage = 2, copiedMessage = 2),
        "Reading · playing" to done.copy(readAloud = ReadAloudUi(messageId = 2, ticks = PreviewReadingTicks)),
        "Reading · paused" to done.copy(
            readAloud = ReadAloudUi(messageId = 2, ticks = PreviewPausedTicks, playing = false),
        ),
        "Fork banner" to done.copy(forkOrigin = PreviewFork),
        "Toast · forked" to done.copy(
            forkOrigin = PreviewFork,
            toast = ChatToastUi(FeatureChatRes.string.chat_toast_forked),
        ),
        "Compare · empty" to ChatUiState(choice = PreviewCompare, recent = PreviewRecent),
        "Compare · thinking" to previewThread(
            question,
            previewPair(first = ReplyStatus.THINKING to emptyList(), second = ReplyStatus.THINKING to emptyList()),
            choice = PreviewCompare,
        ).copy(draft = "Add a leading option"),
        "Compare · streaming" to previewThread(
            question,
            previewPair(
                first = ReplyStatus.STREAMING to PreviewReplyBlocks.take(3),
                second = ReplyStatus.DONE to PreviewAltBlocks,
            ),
            choice = PreviewCompare,
        ),
        "Compare · done" to previewThread(question, comparedDone, choice = PreviewCompare),
        "Compare · continued" to previewThread(question, comparedDone, choice = ModelChoice(PreviewGpt5))
            .copy(toast = ChatToastUi(FeatureChatRes.string.chat_toast_continuing, listOf(PreviewGpt5.shortName))),
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
            onBack = {},
            onModelClick = {},
            onOpenRecent = {},
            onDraftChange = {},
            onSend = {},
            onVoiceClick = {},
            onCopyCode = {},
            onDismissForkBanner = {},
            messageActions = MessageActionCallbacks(),
        )
    }
}
