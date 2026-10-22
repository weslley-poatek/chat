package br.com.weslleycampos.chat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.core.ui.components.ChatToastDefaults
import br.com.weslleycampos.chat.data.chat.ChatRepository
import br.com.weslleycampos.chat.data.chat.models.Conversation
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_send_failed
import kotlinx.coroutines.CancellationException
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
 * `copyText` for code blocks, on [ChatRepository]. Replies stream in the data layer; this only
 * observes them, and derives "a reply is on its way" from the last message, never from a flag of
 * its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel(binds = [ChatViewModel::class])
class ChatViewModel(
    @InjectedParam entry: ChatEntry,
    val navigator: Navigator,
    private val repository: ChatRepository,
    private val times: ChatTimes,
) : ViewModel(), Navigator by navigator {
    val uiState: StateFlow<ChatUiState>
        field = MutableStateFlow(ChatUiState(conversationId = entry.conversationId))

    /** The conversation the thread observes; `null` while it is a new chat. */
    private val openConversation = MutableStateFlow(entry.conversationId)

    /** Moves on "new chat" and on opening a conversation, so an older send cannot pull the thread back. */
    private var generation = 0

    private var recentConversations: List<Conversation> = emptyList()
    private val messageUis = MessageUiCache()
    private var copiedJob: Job? = null
    private var toastJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeRecentConversations().collect { conversations ->
                recentConversations = conversations
                uiState.update { state -> state.copy(recent = conversations.map { it.toRecentUi(times) }) }
            }
        }
        viewModelScope.launch {
            openConversation
                .flatMapLatest { id -> if (id == null) flowOf(null) else observeThread(id) }
                .collect(::show)
        }
    }

    fun onDraftChange(draft: String) = uiState.update { it.copy(draft = draft) }

    /**
     * Sends the draft, the prototype's `send`: the composer empties at once. When nothing was sent,
     * the text comes back to an untouched composer and the toast says so.
     */
    fun onSend() {
        val state = uiState.value
        if (!state.canSend) return
        val text = state.draft
        val sentFrom = generation
        uiState.update { it.copy(draft = "", sending = true) }
        viewModelScope.launch {
            val conversationId = try {
                repository.send(state.conversationId, text)
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
            if (conversationId == null) showToast(FeatureChatRes.string.chat_toast_send_failed)
        }
    }

    /** The prototype's `newChat`: back to the empty state on the default model, keeping the draft. */
    fun onNewChat() {
        generation++
        openConversation.value = null
        clearCopied()
        uiState.update {
            it.copy(
                conversationId = null,
                model = ModelCatalogue.default,
                messages = emptyList(),
                replyInProgress = false,
            )
        }
    }

    /** A "recent" row: opens that conversation, with its model's accent from the first frame. */
    fun onOpenRecent(conversationId: Long) {
        if (conversationId == uiState.value.conversationId) return
        generation++
        openConversation.value = conversationId
        clearCopied()
        val model = recentConversations.firstOrNull { it.id == conversationId }?.model ?: uiState.value.model
        uiState.update {
            it.copy(conversationId = conversationId, model = model, messages = emptyList(), replyInProgress = false)
        }
    }

    /**
     * "copy" on a code block: hands its text as it is now to [copy], the platform clipboard, and
     * reads "copied" for 1200 ms. Another copy restarts that time.
     */
    fun onCopyCode(key: BlockKey, copy: (String) -> Unit) {
        val block = uiState.value.messages.codeBlock(key) ?: return
        copy(block.text)
        copiedJob?.cancel()
        uiState.update { it.copy(copiedBlock = key) }
        copiedJob = viewModelScope.launch {
            delay(CopiedDuration)
            uiState.update { it.copy(copiedBlock = null) }
        }
    }

    private fun observeThread(conversationId: Long): Flow<Thread> = combine(
        repository.observeConversation(conversationId),
        repository.observeMessages(conversationId),
    ) { conversation, messages -> Thread(conversationId, conversation, messages) }

    private fun show(thread: Thread?) {
        val conversation = thread?.conversation
        when {
            thread == null -> uiState.update { it.copy(messages = emptyList(), replyInProgress = false) }
            // Gone, or never there: a new chat rather than a thread that stays empty.
            conversation == null -> onNewChat()
            else -> uiState.update {
                it.copy(
                    conversationId = thread.id,
                    model = conversation.model,
                    messages = messageUis.map(thread.messages, times),
                    replyInProgress = thread.messages.replyInProgress(),
                )
            }
        }
    }

    private fun clearCopied() {
        copiedJob?.cancel()
        uiState.update { it.copy(copiedBlock = null) }
    }

    /** The prototype's `toastMsg`: a new toast replaces the shown one and restarts its time. */
    private fun showToast(message: StringResource) {
        toastJob?.cancel()
        uiState.update { it.copy(toast = message) }
        toastJob = viewModelScope.launch {
            delay(ChatToastDefaults.Duration)
            uiState.update { it.copy(toast = null) }
        }
    }

    private data class Thread(val id: Long, val conversation: Conversation?, val messages: List<Message>)

    companion object {
        /** The prototype's `copy` timer. */
        val CopiedDuration: Duration = 1200.milliseconds
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
