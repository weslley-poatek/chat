package br.com.weslleycampos.chat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.navigation.entries.history.HistoryEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.data.chat.ChatRepository
import br.com.weslleycampos.chat.data.chat.CompareRepository
import br.com.weslleycampos.chat.data.chat.ForkRepository
import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_comparing
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_send_failed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The Chat frame's logic: the prototype's `send`, `newChat`, `loadSeed` for a "recent" row and
 * `copyText` for code blocks, on [ChatRepository], and the Model picker's `openModelSheet`,
 * `mRow.pick` and `mRow.fav` on [ModelRepository]. Replies stream in the data layer; this only
 * observes them, and derives "a reply is on its way" from the last message, never from a flag of
 * its own.
 *
 * What answers is [ChatUiState.choice]: an open conversation's own model, persona and compare, which
 * a pick changes through [ChatRepository.choose]; or, in a new chat, the default model alone until
 * the user picks, which only this ViewModel keeps and the first send hands to [ChatRepository.send].
 * While two models are compared, a send is answered by a compare pair, which streams in the data
 * layer like a reply.
 *
 * A tap on a message, its action row, the read-aloud bar and the fork banner are [messageActions],
 * on [ForkRepository]. The compare button and a compare card's "continue with this →" are
 * [compareActions], on [CompareRepository]. A toast carried over by [ChatToastRelay], a fork's,
 * shows as this opens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel(binds = [ChatViewModel::class])
class ChatViewModel(
    @InjectedParam entry: ChatEntry,
    val navigator: Navigator,
    private val repository: ChatRepository,
    private val models: ModelRepository,
    private val times: ChatTimes,
    forks: ForkRepository,
    compares: CompareRepository,
    relay: ChatToastRelay,
) : ViewModel(), Navigator by navigator {
    val uiState: StateFlow<ChatUiState>
        field = MutableStateFlow(ChatUiState(conversationId = entry.conversationId))

    /** The conversation the thread observes; `null` while it is a new chat. */
    private val openConversation = MutableStateFlow(entry.conversationId)

    /** Moves on "new chat" and on opening a conversation, so an older send cannot pull the thread back. */
    private var generation = 0

    private var recentConversations: List<Conversation> = emptyList()

    /**
     * The open conversation as last observed. Its choice reaches the chip only when the conversation
     * itself changes, so a streaming tick that arrives before a pick is saved cannot take it back.
     */
    private var shownConversation: Conversation? = null

    /** What a new chat starts on, and "new chat" returns to: `ModelRepository.observeDefaultModel`. */
    private var defaultModel: ChatModel = ModelCatalogue.default

    /** What answers, as the picker changes it, with a new chat's own pick while it does not follow [defaultModel]. */
    private val choices = ChatChoices(
        scope = viewModelScope,
        state = uiState,
        repository = repository,
        generation = { generation },
        ownChoice = { shownConversation?.choice },
    )
    private val messageUis = MessageUiCache()

    /**
     * The prototype's `copy` timer: "copied" on a code block or a message for [CopiedDuration], one
     * at a time, as its single `copiedKey`.
     */
    private val copied = TimedValue<CopiedLabel>(viewModelScope, CopiedDuration) { label ->
        uiState.update {
            it.copy(
                copiedBlock = (label as? CopiedLabel.OfCode)?.key,
                copiedMessage = (label as? CopiedLabel.OfMessage)?.messageId,
            )
        }
    }

    /** The prototype's `toastMsg`: a new toast replaces the shown one and restarts its time. */
    private val toast = TimedValue<ChatToastUi>(viewModelScope, ChatToastDefaults.Duration) { message ->
        uiState.update { it.copy(toast = message) }
    }

    /** A tap on a message, its action row, the read-aloud bar and the fork banner's "×". */
    val messageActions = MessageActions(
        scope = viewModelScope,
        state = uiState,
        forks = forks,
        navigator = navigator,
        copied = copied,
        toast = toast,
        relay = relay,
    )

    /** The Model picker's compare button and a compare card's "continue with this →". */
    val compareActions = CompareActions(
        scope = viewModelScope,
        state = uiState,
        choices = choices,
        compares = compares,
        toast = toast,
    )

    init {
        relay.take()?.let { toast.show(ChatToastUi(it)) }
        viewModelScope.launch {
            models.observeDefaultModel().collect { model ->
                defaultModel = model
                // A new chat that nothing was picked in follows the default model, wherever it is changed.
                uiState.update {
                    val follows = it.conversationId == null && choices.newChatChoice == null
                    if (follows) it.copy(choice = ModelChoice(model)) else it
                }
            }
        }
        viewModelScope.launch {
            models.observeFavorites().collect { favorites -> uiState.update { it.copy(favorites = favorites) } }
        }
        viewModelScope.launch {
            models.observePersonas().collect { personas -> uiState.update { it.copy(personas = personas) } }
        }
        viewModelScope.launch {
            repository.observeRecentConversations().collect { conversations ->
                recentConversations = conversations
                uiState.update { state -> state.copy(recent = conversations.map { it.toRecentUi(times) }) }
            }
        }
        viewModelScope.launch {
            openConversation
                .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeThread(id) }
                .collect(::show)
        }
    }

    fun onDraftChange(draft: String) = uiState.update { it.copy(draft = draft) }

    /**
     * Sends the draft, the prototype's `send`: the composer empties at once. A new chat's first send
     * creates the conversation on the new chat's choice. When nothing was sent, the text comes back
     * to an untouched composer and the toast says so.
     */
    fun onSend() {
        val state = uiState.value
        if (!state.canSend) return
        val text = state.draft
        val sentFrom = generation
        uiState.update { it.copy(draft = "", sending = true) }
        viewModelScope.launch {
            val conversationId = try {
                repository.send(
                    conversationId = state.conversationId,
                    text = text,
                    newChat = state.choice.takeIf { state.conversationId == null },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // The prototype has no failure to show; the toast below says it, the draft comes back.
                null
            }
            if (conversationId != null && sentFrom == generation) {
                openConversation.value = conversationId
                uiState.update { it.copy(conversationId = conversationId) }
            }
            uiState.update {
                it.copy(
                    sending = false,
                    draft = if (conversationId == null && it.draft.isEmpty()) text else it.draft,
                )
            }
            if (conversationId == null) toast.show(ChatToastUi(FeatureChatRes.string.chat_toast_send_failed))
        }
    }

    /**
     * The prototype's `newChat`: back to the empty state on the default model, with no persona and
     * compare off, keeping the draft.
     */
    fun onNewChat() {
        generation++
        openConversation.value = null
        shownConversation = null
        choices.reset()
        copied.clear()
        messageActions.reset()
        uiState.update {
            it.copy(
                conversationId = null,
                choice = ModelChoice(defaultModel),
                messages = emptyList(),
                replyInProgress = false,
                forkOrigin = null,
            )
        }
    }

    /** A "recent" row: opens that conversation, with its model, persona and accent from the first frame. */
    fun onOpenRecent(conversationId: Long) {
        if (conversationId == uiState.value.conversationId) return
        generation++
        openConversation.value = conversationId
        shownConversation = null
        copied.clear()
        messageActions.reset()
        val choice = recentConversations.firstOrNull { it.id == conversationId }?.choice ?: uiState.value.choice
        uiState.update {
            it.copy(
                conversationId = conversationId,
                choice = choice,
                messages = emptyList(),
                replyInProgress = false,
                forkOrigin = null,
            )
        }
    }

    /** The model chip, the prototype's `openModelSheet`: the Model picker opens. */
    fun onOpenModelPicker() = uiState.update { it.copy(modelPickerOpen = true) }

    /** A tap on the scrim, a drag down or Back: the sheet closes, picking nothing. */
    fun onDismissModelPicker() = uiState.update { it.copy(modelPickerOpen = false) }

    /**
     * A row of the sheet, [row] being what it picks, the prototype's `mRow.pick` under
     * [ModelChoice.picked]: a model alone, a persona, or while compare is on the second model, which
     * the toast names, "comparing sonnet-4.5 ⇄ gpt-5". What it makes answers from now on, the accent
     * follows at once and the sheet closes. While comparing, the first model's own row changes
     * nothing and the sheet stays open. In an open conversation the choice is saved with
     * [ChatRepository.choose]; if that changes nothing, the chip shows the conversation's own
     * choice again. In a new chat it is kept as the new chat's choice for its first send.
     */
    fun onPick(row: ModelChoice) {
        val choice = uiState.value.choice.picked(row) ?: return
        uiState.update { it.copy(modelPickerOpen = false) }
        choices.apply(choice)
        choice.comparing?.let { second ->
            val models = listOf(choice.model.shortName, second.shortName)
            toast.show(ChatToastUi(FeatureChatRes.string.chat_toast_comparing, models))
        }
    }

    /**
     * A row's "★" / "☆", the prototype's `mRow.fav`: stars or unstars [model] without picking it;
     * the sheet stays open and the stars follow the stored favorites.
     */
    fun onToggleFavorite(model: ChatModel) {
        val favorite = uiState.value.favorites.none { it.id == model.id }
        viewModelScope.launch {
            try {
                models.setFavorite(model, favorite)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // Nothing was stored, so the star keeps showing what is.
            }
        }
    }

    /**
     * "copy" on a code block: hands its text as it is now to [copy], the platform clipboard, and
     * reads "copied" for 1200 ms. Another copy, of a block or a message, restarts that time.
     */
    fun onCopyCode(key: BlockKey, copy: (String) -> Unit) {
        val block = uiState.value.messages.codeBlock(key) ?: return
        copy(block.text)
        copied.show(CopiedLabel.OfCode(key))
    }

    private fun show(thread: ObservedThread?) {
        val conversation = thread?.conversation
        when {
            thread == null -> {
                uiState.update { it.copy(messages = emptyList(), replyInProgress = false, forkOrigin = null) }
            }
            // Gone, or never there: a new chat rather than a thread that stays empty.
            conversation == null -> onNewChat()
            else -> {
                val conversationChanged = conversation != shownConversation
                shownConversation = conversation
                uiState.update {
                    it.copy(
                        conversationId = thread.id,
                        choice = if (conversationChanged) conversation.choice else it.choice,
                        messages = messageUis.map(thread.messages, times),
                        replyInProgress = thread.messages.replyInProgress(),
                        forkOrigin = conversation.fork?.takeUnless { origin -> origin.bannerDismissed },
                    )
                }
                messageActions.onThread(thread.messages)
            }
        }
    }

    companion object {
        /** The prototype's `copy` timer. */
        val CopiedDuration: Duration = 1200.milliseconds
    }
}

/**
 * The History button, the prototype's `goHistory`: History opens over the chat, which stays as it is
 * underneath for History's back. Only a navigation, so it extends the ViewModel's [Navigator] rather
 * than adding to its members.
 */
fun ChatViewModel.onOpenHistory() = navigate(HistoryEntry)

private data class ObservedThread(val id: Long, val conversation: Conversation?, val messages: List<Message>)

private fun ChatRepository.observeThread(conversationId: Long): Flow<ObservedThread> = combine(
    observeConversation(conversationId),
    observeMessages(conversationId),
) { conversation, messages -> ObservedThread(conversationId, conversation, messages) }

/**
 * A value shown for [duration] and then cleared, through [onChange]: showing another one restarts
 * the time, and [clear] ends it at once.
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

    fun clear() {
        job?.cancel()
        onChange(null)
    }
}

/**
 * Maps messages for the thread, reusing the last mapping of every message that did not change, so
 * a streaming tick re-tokenises only the reply that grew and the thread redraws only that row.
 */
internal class MessageUiCache {
    private var last: Map<Message, MessageUi> = emptyMap()

    fun map(messages: List<Message>, times: ChatTimes): List<MessageUi> {
        val mapped = messages.associateWith { last[it] ?: it.toUi(times) }
        last = mapped
        return messages.map { mapped.getValue(it) }
    }
}
