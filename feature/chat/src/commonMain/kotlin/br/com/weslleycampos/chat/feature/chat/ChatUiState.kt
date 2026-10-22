package br.com.weslleycampos.chat.feature.chat

import androidx.compose.runtime.Immutable
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerUiState
import br.com.weslleycampos.chat.feature.chat.picker.ModelPickerVariant
import br.com.weslleycampos.chat.feature.chat.picker.modelPickerUiState
import org.jetbrains.compose.resources.StringResource

/**
 * Everything the Chat frame shows, the Model picker sheet over it included.
 *
 * @property conversationId The open conversation, or `null` for a new chat, which shows the empty state.
 * @property choice What answers the next message: the open conversation's model and persona, or the
 *   new chat's, which starts on the default model and is whatever the picker picked since. It is
 *   the chip, the empty state, the composer placeholder, the sheet's "●" and, through [model]'s
 *   palette, the accent.
 * @property messages The thread, oldest first, as the prototype's message view-model (810–832).
 * @property recent The empty state's "recent" rows, newest first.
 * @property draft The composer's text.
 * @property replyInProgress The prototype's `streaming`: the last reply is thinking or streaming.
 * @property sending A send is on its way to the database.
 * @property copiedBlock The code block whose header reads "copied", the prototype's `copiedKey`.
 * @property toast The toast's text while it shows.
 * @property favorites The picker's "favorites", in catalogue order.
 * @property personas The picker's "personas", in their order.
 * @property modelPickerOpen The Model picker sheet shows: the prototype's `sheet:'chat'`.
 */
@Immutable
data class ChatUiState(
    val conversationId: Long? = null,
    val choice: ModelChoice = ModelChoice(ModelCatalogue.default),
    val messages: List<MessageUi> = emptyList(),
    val recent: List<RecentConversationUi> = emptyList(),
    val draft: String = "",
    val replyInProgress: Boolean = false,
    val sending: Boolean = false,
    val copiedBlock: BlockKey? = null,
    val toast: StringResource? = null,
    val favorites: List<ChatModel> = emptyList(),
    val personas: List<Persona> = emptyList(),
    val modelPickerOpen: Boolean = false,
) {
    /** The model that answers: [choice]'s, the persona's own while one answers. */
    val model: ChatModel get() = choice.model

    /** The persona that answers, or `null` for the model on its own. */
    val persona: Persona? get() = choice.persona
}

/** A new chat: the prototype's `isEmpty`. */
val ChatUiState.showsEmptyState: Boolean get() = conversationId == null

/** The prototype's `canSend`, without attachments: some text, and no reply on its way. */
val ChatUiState.canSend: Boolean get() = draft.isNotBlank() && !replyInProgress && !sending

/** How many lines tall the composer is at least. */
val ChatUiState.draftRows: Int get() = draftRows(draft)

/** The model chip: "Code reviewer · sonnet-4.5" with a persona, else the model's full name. */
val ChatUiState.chipLabel: String get() = chipLabel(choice)

/** The chat's Model picker sheet, marking [ChatUiState.choice]. */
val ChatUiState.modelPicker: ModelPickerUiState
    get() = modelPickerUiState(
        variant = ModelPickerVariant.Chat,
        favorites = favorites,
        personas = personas,
        active = choice,
    )

/** Which code block a "copy" belongs to: the prototype's `msg.id + '-' + i`. */
@Immutable
data class BlockKey(val messageId: Long, val index: Int)

/** One message of the thread, with its meta row's parts already formatted. */
@Immutable
sealed interface MessageUi {
    val id: Long

    /** Local `HH:MM` of when it was sent. */
    val time: String

    @Immutable
    data class User(override val id: Long, override val time: String, val text: String) : MessageUi

    /**
     * @property modelShortName The model that wrote it, "sonnet-4.5".
     * @property personaName The persona it was written as, "Code reviewer", or `null`: its own,
     *   whatever answers the conversation now.
     * @property latency "0.8s" once the reply is done, else `null`.
     * @property interrupted It stopped before it finished; it shows what it revealed, without a caret.
     * @property thinking No block yet while in progress: the lone blinking caret.
     */
    @Immutable
    data class Assistant(
        override val id: Long,
        override val time: String,
        val modelShortName: String,
        val personaName: String?,
        val latency: String?,
        val interrupted: Boolean,
        val thinking: Boolean,
        val blocks: List<BlockUi>,
    ) : MessageUi
}

/** One block of a reply, as drawn. Only the last block of a streaming reply has the caret. */
@Immutable
sealed interface BlockUi {
    val key: BlockKey

    @Immutable
    data class Paragraph(override val key: BlockKey, val text: String, val caret: Boolean) : BlockUi

    /** [tokens] are [text] split for syntax colouring. */
    @Immutable
    data class Code(
        override val key: BlockKey,
        val language: String,
        val text: String,
        val tokens: List<CodeToken>,
        val caret: Boolean,
    ) : BlockUi

    /** The prototype draws no caret in a list. */
    @Immutable
    data class NumberedList(override val key: BlockKey, val items: List<NumberedItem>) : BlockUi
}

/** One list item: [number] is "1.", "2.", … */
@Immutable
data class NumberedItem(val number: String, val text: String)

/** A "recent" row: the conversation's title and when it was last updated. */
@Immutable
data class RecentConversationUi(val id: Long, val title: String, val time: String)

/** The message as the thread draws it. */
fun Message.toUi(times: ChatTimes): MessageUi = when (this) {
    is Message.User -> MessageUi.User(id = id, time = times.messageTime(createdAt), text = text)
    is Message.Assistant -> toUi(time = times.messageTime(createdAt))
}

/** The reply as the thread draws it, with [time] already formatted. */
fun Message.Assistant.toUi(time: String): MessageUi.Assistant {
    val streaming = status == ReplyStatus.STREAMING
    return MessageUi.Assistant(
        id = id,
        time = time,
        modelShortName = model.shortName,
        personaName = persona?.name,
        latency = latency?.takeIf { status == ReplyStatus.DONE }?.let(::formatLatency),
        interrupted = status == ReplyStatus.INTERRUPTED,
        thinking = status.isInProgress && blocks.isEmpty(),
        blocks = blocks.mapIndexed { index, block ->
            block.toUi(key = BlockKey(messageId = id, index = index), caret = streaming && index == blocks.lastIndex)
        },
    )
}

private fun Block.toUi(key: BlockKey, caret: Boolean): BlockUi = when (this) {
    is Block.Paragraph -> BlockUi.Paragraph(key = key, text = text, caret = caret)
    is Block.Code -> BlockUi.Code(key = key, language = language, text = text, tokens = tokenize(text), caret = caret)
    is Block.NumberedList -> BlockUi.NumberedList(
        key = key,
        items = items.mapIndexed { index, item -> NumberedItem(number = "${index + 1}.", text = item) },
    )
}

/** The conversation as a "recent" row. */
fun Conversation.toRecentUi(times: ChatTimes): RecentConversationUi =
    RecentConversationUi(id = id, title = title, time = times.recentTime(updatedAt))

/** The prototype's `streaming`, derived from the data: the last message is a reply still on its way. */
fun List<Message>.replyInProgress(): Boolean = (lastOrNull() as? Message.Assistant)?.status?.isInProgress == true

/** The code block with this [key], for "copy". */
fun List<MessageUi>.codeBlock(key: BlockKey): BlockUi.Code? = asSequence()
    .filterIsInstance<MessageUi.Assistant>()
    .filter { it.id == key.messageId }
    .flatMap { it.blocks }
    .filterIsInstance<BlockUi.Code>()
    .firstOrNull { it.key == key }
