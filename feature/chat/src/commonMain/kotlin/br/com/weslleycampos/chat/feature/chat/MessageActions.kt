package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.core.navigation.Navigator
import br.com.weslleycampos.chat.core.navigation.entries.chat.ChatEntry
import br.com.weslleycampos.chat.data.chat.ForkRepository
import br.com.weslleycampos.chat.data.chat.models.Message
import br.com.weslleycampos.chat.data.chat.models.ReplyStatus
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_fork_failed
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_forked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.Single

/**
 * What a tap on a message offers, the prototype's `m.select` and its action row (frame lines
 * 208–215): one message selected at a time, its "copy", "⑂ fork here", a reply's "read aloud" on
 * [player] and a user message's "retry", and the fork banner's "×". It writes the chat's [state]
 * for [ChatViewModel], which owns it, and is kept apart from it: the ViewModel is at detekt's
 * function limit.
 *
 * Only a message that is not on its way can be selected or acted on: a reply thinking or streaming
 * has no action row, as the prototype's `showActions`.
 *
 * @param autoRead Row 10's "Read replies aloud": while it answers `true`, [onReplyDone] reads each
 *   reply that finishes. There is no such preference yet, so it answers `false`.
 */
class MessageActions internal constructor(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatUiState>,
    private val forks: ForkRepository,
    private val navigator: Navigator,
    private val copied: TimedValue<CopiedLabel>,
    private val toast: TimedValue<StringResource>,
    private val relay: ChatToastRelay,
    private val autoRead: () -> Boolean = { false },
) {
    /** The read-aloud bar: one reply at a time. */
    val player = ReadAloudPlayer(scope) { reading -> state.update { it.copy(readAloud = reading) } }

    private var forking: Job? = null

    /** The thread as last observed, to tell when its reply finishes. */
    private var thread: List<Message> = emptyList()

    /**
     * A tap on a message, the prototype's `m.select`: it shows that message's actions, in place of
     * another's; a tap on the selected one hides them. A reply on its way is not selectable.
     */
    fun onSelect(messageId: Long) = state.update { current ->
        when {
            current.actionable(messageId) == null -> current
            current.selectedMessage == messageId -> current.copy(selectedMessage = null)
            else -> current.copy(selectedMessage = messageId)
        }
    }

    /**
     * "copy", the prototype's `copyAll`: hands the message's [copyText] to [copy], the platform
     * clipboard, and reads "copied" for [ChatViewModel.CopiedDuration], in place of any other
     * "copied". The selection stays.
     */
    fun onCopy(messageId: Long, copy: (String) -> Unit) {
        val message = state.value.actionable(messageId) ?: return
        copy(message.copyText())
        copied.show(CopiedLabel.OfMessage(messageId))
    }

    /**
     * "⑂ fork here", the prototype's `fork`: a new conversation copied up to [messageId], which
     * replaces this chat on the back stack, where the toast says "forked · new conversation". When
     * nothing was forked, the chat stays and the toast says so.
     */
    fun onFork(messageId: Long) {
        val current = state.value
        val conversationId = current.conversationId ?: return
        if (current.actionable(messageId) == null || forking?.isActive == true) return
        state.update { it.copy(selectedMessage = null) }
        forking = scope.launch {
            val fork = try {
                forks.fork(conversationId = conversationId, messageId = messageId)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                null
            }
            if (fork == null) {
                toast.show(FeatureChatRes.string.chat_toast_fork_failed)
            } else {
                relay.post(FeatureChatRes.string.chat_toast_forked)
                navigator.navigate(ChatEntry(fork)) { popUpTo<ChatEntry> { inclusive = true } }
            }
        }
    }

    /**
     * A reply's "read aloud", the prototype's `toggleRead`: the bar starts under it, in place of any
     * other reply's, and the selection clears; on the reply being read it plays or pauses.
     */
    fun onReadAloud(messageId: Long) {
        if (state.value.actionable(messageId) !is MessageUi.Assistant) return
        val starts = player.messageId != messageId
        player.read(messageId)
        if (starts) state.update { it.copy(selectedMessage = null) }
    }

    /**
     * A user message's "retry", the prototype's `retry`: its text replaces the draft and the
     * selection clears. Nothing is sent.
     */
    fun onRetry(messageId: Long) {
        val message = state.value.actionable(messageId) as? MessageUi.User ?: return
        state.update { it.copy(draft = message.text, selectedMessage = null) }
    }

    /** The fork banner's "×", the prototype's `clearFork`: hidden for good, once it is stored. */
    fun onDismissForkBanner() {
        val conversationId = state.value.conversationId ?: return
        scope.launch {
            try {
                forks.dismissForkBanner(conversationId)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // Nothing was stored, so the banner keeps showing what is.
            }
        }
    }

    /**
     * The chat leaves the screen, for History, another app or a rotation, as the prototype's `go()`:
     * the selection clears and a read pauses where it is.
     */
    fun onLeave() {
        player.pause()
        state.update { it.copy(selectedMessage = null) }
    }

    /**
     * A reply in the open thread just finished, the prototype's `if (autoRead) startRead(id)`: row
     * 10's "Read replies aloud" reads it through [autoRead].
     */
    fun onReplyDone(messageId: Long) {
        if (autoRead()) onReadAloud(messageId)
    }

    /** "new chat" or another conversation: nothing is selected and nothing is read. */
    internal fun reset() {
        player.onStop()
        thread = emptyList()
        state.update { it.copy(selectedMessage = null) }
    }

    /** The open thread changed: a reply that finished goes to [onReplyDone]. */
    internal fun onThread(messages: List<Message>) {
        val finished = finishedReply(previous = thread, current = messages)
        thread = messages
        finished?.let(::onReplyDone)
    }
}

/**
 * The last reply of [current] when it is done and was on its way in [previous]: a reply that has
 * just finished streaming. `null` otherwise.
 */
internal fun finishedReply(previous: List<Message>, current: List<Message>): Long? {
    val reply = current.lastOrNull() as? Message.Assistant ?: return null
    val before = previous.lastOrNull { it.id == reply.id } as? Message.Assistant ?: return null
    return reply.id.takeIf { reply.status == ReplyStatus.DONE && before.status.isInProgress }
}

/** What reads "copied": a code block's header or a message's "copy", one at a time, as the prototype's `copiedKey`. */
internal sealed interface CopiedLabel {
    data class OfCode(val key: BlockKey) : CopiedLabel

    data class OfMessage(val messageId: Long) : CopiedLabel
}

/**
 * Carries a toast from a chat entry to the one that replaces it: "⑂ fork here" opens the fork in a
 * new entry, whose ViewModel shows "forked · new conversation".
 */
@Single
class ChatToastRelay {
    private var pending: StringResource? = null

    /** Keeps [message] for the next chat entry. */
    fun post(message: StringResource) {
        pending = message
    }

    /** The toast kept for this entry, once. */
    fun take(): StringResource? = pending.also { pending = null }
}

/** The message with this id, when it can be selected and acted on. */
private fun ChatUiState.actionable(messageId: Long): MessageUi? =
    messages.firstOrNull { it.id == messageId }?.takeIf { it.selectable }
