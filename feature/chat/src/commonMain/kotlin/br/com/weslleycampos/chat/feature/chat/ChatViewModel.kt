package br.com.weslleycampos.chat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.data.chat.ChatRepository
import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
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
import org.jetbrains.compose.resources.StringResource
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
 * What answers is [ChatUiState.choice]: an open conversation's own model and persona, which a pick
 * changes through [ChatRepository.choose]; or, in a new chat, the default model until the user
 * picks, which only this ViewModel keeps and the first send hands to [ChatRepository.send].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel(binds = [ChatViewModel::class])
class ChatViewModel(
    @InjectedParam entry: ChatEntry,
    val navigator: Navigator,
    private val repository: ChatRepository,
    private val models: ModelRepository,
    private val times: ChatTimes,
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

    /** The new chat's own pick, or `null` while it follows [defaultModel]. */
    private var newChatChoice: ModelChoice? = null

    /** Moves on every pick in an open conversation, so only the latest one is undone when refused. */
    private var picks = 0
    private val messageUis = MessageUiCache()

    /** The prototype's `copy` timer: "copied" on the block for [CopiedDuration]. */
    private val copied = TimedValue<BlockKey>(viewModelScope, CopiedDuration) { key ->
        uiState.update { it.copy(copiedBlock = key) }
    }

    /** The prototype's `toastMsg`: a new toast replaces the shown one and restarts its time. */
    private val toast = TimedValue<StringResource>(viewModelScope, ChatToastDefaults.Duration) { message ->
        uiState.update { it.copy(toast = message) }
    }

    init {
        viewModelScope.launch {
            models.observeDefaultModel().collect { model ->
                defaultModel = model
                // A new chat that nothing was picked in follows the default model, wherever it is changed.
                uiState.update {
                    if (it.conversationId == null && newChatChoice == null) it.copy(choice = ModelChoice(model)) else it
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
            if (conversationId == null) toast.show(FeatureChatRes.string.chat_toast_send_failed)
        }
    }

    /**
     * The prototype's `newChat`: back to the empty state on the default model, with no persona,
     * keeping the draft.
     */
    fun onNewChat() {
        generation++
        openConversation.value = null
        shownConversation = null
        newChatChoice = null
        copied.clear()
        uiState.update {
            it.copy(
                conversationId = null,
                choice = ModelChoice(defaultModel),
                messages = emptyList(),
                replyInProgress = false,
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
        val choice = recentConversations.firstOrNull { it.id == conversationId }?.choice ?: uiState.value.choice
        uiState.update {
            it.copy(conversationId = conversationId, choice = choice, messages = emptyList(), replyInProgress = false)
        }
    }

    /** The model chip, the prototype's `openModelSheet`: the Model picker opens. */
    fun onOpenModelPicker() = uiState.update { it.copy(modelPickerOpen = true) }

    /** A tap on the scrim, a drag down or Back: the sheet closes, picking nothing. */
    fun onDismissModelPicker() = uiState.update { it.copy(modelPickerOpen = false) }

    /**
     * A row of the sheet, the prototype's `mRow.pick`: [choice] answers from now on, the accent
     * follows at once and the sheet closes. In an open conversation it is saved with
     * [ChatRepository.choose]; if that changes nothing, the chip shows the conversation's own
     * choice again. In a new chat it is kept as the new chat's choice for its first send.
     */
    fun onPick(choice: ModelChoice) {
        val conversationId = uiState.value.conversationId
        uiState.update { it.copy(choice = choice, modelPickerOpen = false) }
        if (conversationId == null) {
            newChatChoice = choice
            return
        }
        val pick = ++picks
        val pickedFrom = generation
        viewModelScope.launch {
            val chosen = try {
                repository.choose(conversationId, choice)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                false
            }
            // Refused: unless another pick or another conversation came since, show its own choice again.
            val latest = pick == picks && pickedFrom == generation
            val own = shownConversation?.choice
            if (!chosen && latest && own != null) uiState.update { it.copy(choice = own) }
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
     * reads "copied" for 1200 ms. Another copy restarts that time.
     */
    fun onCopyCode(key: BlockKey, copy: (String) -> Unit) {
        val block = uiState.value.messages.codeBlock(key) ?: return
        copy(block.text)
        copied.show(key)
    }

    private fun show(thread: ObservedThread?) {
        val conversation = thread?.conversation
        when {
            thread == null -> uiState.update { it.copy(messages = emptyList(), replyInProgress = false) }
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
                    )
                }
            }
        }
    }

    companion object {
        /** The prototype's `copy` timer. */
        val CopiedDuration: Duration = 1200.milliseconds
    }
}

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
