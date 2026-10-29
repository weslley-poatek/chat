package br.com.weslleycampos.chat.feature.chat

import androidx.compose.runtime.Immutable
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.Block
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.CompareSide
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.ForkOrigin
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
 * @property choice What answers the next message: the open conversation's model, persona and compare,
 *   or the new chat's, which starts on the default model alone and is whatever the picker picked
 *   since. It is the chip, the empty state, the composer placeholder, the sheet's "●", "◐" and
 *   compare button and, through [model]'s palette, the accent: the primary model's while comparing.
 * @property messages The thread, oldest first, as the prototype's message view-model (810–832).
 * @property recent The empty state's "recent" rows, newest first.
 * @property draft The composer's text.
 * @property replyInProgress The prototype's `streaming`: the last reply, or a side of the last compare
 *   pair, is thinking or streaming.
 * @property sending A send is on its way to the database.
 * @property copiedBlock The code block whose header reads "copied", the prototype's `copiedKey`.
 * @property toast The toast while it shows.
 * @property models The picker's models, by vendor: every one the Fuelix key can use, or the
 *   catalogue's until Fuelix has listed them.
 * @property favorites The picker's "favorites", in the order of [models].
 * @property personas The picker's "personas", in their order.
 * @property modelPickerOpen The Model picker sheet shows: the prototype's `sheet:'chat'`.
 * @property selectedMessage The message whose action row shows, the prototype's `selectedMsg`.
 * @property copiedMessage The message whose "copy" reads "copied", the prototype's `copiedKey` for a
 *   message; at most one of it and [copiedBlock] is set.
 * @property readAloud The read-aloud bar, under the reply it reads, or `null`.
 * @property forkOrigin Where the open conversation was forked from while its fork banner shows, the
 *   prototype's `forkFrom`; `null` for a conversation that is no fork or whose banner was dismissed.
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
    val toast: ChatToastUi? = null,
    val models: List<ChatModel> = ModelCatalogue.models,
    val favorites: List<ChatModel> = emptyList(),
    val personas: List<Persona> = emptyList(),
    val modelPickerOpen: Boolean = false,
    val selectedMessage: Long? = null,
    val copiedMessage: Long? = null,
    val readAloud: ReadAloudUi? = null,
    val forkOrigin: ForkOrigin? = null,
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

/**
 * The model chip: "sonnet-4.5 ⇄ gpt-5" while comparing, "Code reviewer · sonnet-4.5" with a persona,
 * else the model's full name.
 */
val ChatUiState.chipLabel: String get() = chipLabel(choice)

/** The composer's placeholder: "message sonnet-4.5…", or "ask sonnet-4.5 and gpt-5…" while comparing. */
val ChatUiState.composerPlaceholder: String get() = composerPlaceholder(choice)

/** The chat's Model picker sheet, marking [ChatUiState.choice] and its compare. */
val ChatUiState.modelPicker: ModelPickerUiState
    get() = modelPickerUiState(
        variant = ModelPickerVariant.Chat,
        models = models,
        favorites = favorites,
        personas = personas,
        active = choice,
    )

/** The fork banner under the top bar, after "⑂ ": "forked from “useDebounce hook” at message 2". */
val ChatUiState.forkBanner: String? get() = forkOrigin?.let(::forkBannerText)

/**
 * A toast: [message], formatted with [args] ("comparing %1$s ⇄ %2$s"), the prototype's `toastMsg`.
 * A chat toast has at most two args, the models it names.
 */
@Immutable
data class ChatToastUi(val message: StringResource, val args: List<String> = emptyList())

/**
 * Which code block a "copy" belongs to: the prototype's `msg.id + '-' + i`, or for a compare card
 * its side's id, `msg.id + '-' + side + i`.
 */
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
     * @property inProgress It is thinking or streaming, so it cannot be selected yet.
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
        val inProgress: Boolean = false,
    ) : MessageUi

    /**
     * A compare reply, the prototype's `pair` (frame lines 186–206): two models' answers to one
     * message, each in a card of the carousel. Its meta row reads "sonnet-4.5 ⇄ gpt-5 · 14:05".
     *
     * @property id The pair's id, its first side's: "continue with this →" names the pair by it.
     * @property sides Always two: the conversation's model's card, then the compared model's.
     */
    @Immutable
    data class ComparePair(
        override val id: Long,
        override val time: String,
        val sides: List<CompareSideUi>,
    ) : MessageUi {
        /** A side is still thinking or streaming. */
        val inProgress: Boolean get() = sides.any { it.inProgress }
    }
}

/**
 * One card of a compare pair: a model's reply, drawn in that model's accent.
 *
 * @property id The side's own id, which its code blocks' [BlockKey]s carry.
 * @property index Its place in the pair, `0` or `1`, which "continue with this →" hands on.
 * @property modelName The card's header, the model's full name, "Claude Sonnet 4.5".
 * @property modelShortName "sonnet-4.5": the pair's meta and "continuing with sonnet-4.5".
 * @property palette The card's accent: its 7 dp dot and its caret.
 * @property latency "1.9s" once the side is done, else `null`.
 * @property interrupted It stopped before it finished: what it revealed, without a caret.
 * @property thinking No block yet while in progress: the lone caret.
 * @property inProgress It is thinking or streaming.
 * @property done Every block is revealed: the card offers "continue with this →".
 */
@Immutable
data class CompareSideUi(
    val id: Long,
    val index: Int,
    val modelName: String,
    val modelShortName: String,
    val palette: ChatPalette,
    val blocks: List<BlockUi>,
    val latency: String?,
    val interrupted: Boolean,
    val thinking: Boolean,
    val inProgress: Boolean,
    val done: Boolean,
)

/**
 * A tap selects it and shows its action row: every message but a reply thinking or streaming, as
 * the prototype's `showActions`, and never a compare pair, which has no select handler.
 */
val MessageUi.selectable: Boolean
    get() = when (this) {
        is MessageUi.User -> true
        is MessageUi.Assistant -> !inProgress
        is MessageUi.ComparePair -> false
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
    is Message.ComparePair -> toUi(time = times.messageTime(createdAt))
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
        inProgress = status.isInProgress,
    )
}

/** The compare pair as the thread draws it, with [time] already formatted: a card per side. */
fun Message.ComparePair.toUi(time: String): MessageUi.ComparePair =
    MessageUi.ComparePair(id = id, time = time, sides = sides.mapIndexed { index, side -> side.toUi(index) })

/**
 * A card: like a reply, its last paragraph streams with the caret, but the prototype's cards draw
 * none inside code (frame line 197).
 */
private fun CompareSide.toUi(index: Int): CompareSideUi {
    val streaming = status == ReplyStatus.STREAMING
    return CompareSideUi(
        id = id,
        index = index,
        modelName = model.name,
        modelShortName = model.shortName,
        palette = model.palette,
        blocks = blocks.mapIndexed { blockIndex, block ->
            block.toUi(
                key = BlockKey(messageId = id, index = blockIndex),
                caret = streaming && blockIndex == blocks.lastIndex && block !is Block.Code,
            )
        },
        latency = latency?.takeIf { status == ReplyStatus.DONE }?.let(::formatLatency),
        interrupted = status == ReplyStatus.INTERRUPTED,
        thinking = status.isInProgress && blocks.isEmpty(),
        inProgress = status.isInProgress,
        done = status == ReplyStatus.DONE,
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

/**
 * The prototype's `streaming`, derived from the data: the last message is a reply still on its way,
 * or a compare pair with a side still on its way.
 */
fun List<Message>.replyInProgress(): Boolean = when (val last = lastOrNull()) {
    is Message.Assistant -> last.status.isInProgress
    is Message.ComparePair -> last.isInProgress
    is Message.User, null -> false
}

/** The code block with this [key], for "copy": a reply's, or a compare card's. */
fun List<MessageUi>.codeBlock(key: BlockKey): BlockUi.Code? = asSequence()
    .flatMap { message ->
        when (message) {
            is MessageUi.Assistant -> if (message.id == key.messageId) message.blocks else emptyList()
            is MessageUi.ComparePair -> message.sides.filter { it.id == key.messageId }.flatMap { it.blocks }
            is MessageUi.User -> emptyList()
        }
    }
    .filterIsInstance<BlockUi.Code>()
    .firstOrNull { it.key == key }
