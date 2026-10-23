package br.com.weslleycampos.chat.feature.chat

import br.com.weslleycampos.chat.data.chat.ChatRepository
import br.com.weslleycampos.chat.data.chat.CompareRepository
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_continue_failed
import br.com.weslleycampos.chat.feature.chat.resources.chat_toast_continuing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Compare mode's own controls, the prototype's `toggleCompare` and `p.keep` (frame lines 823 and
 * 930): the Model picker's compare button and a compare card's "continue with this →". It writes
 * the chat's [state] for [ChatViewModel], which owns it, and is kept apart from it: the ViewModel is
 * at detekt's function limit. Picking the second model is a row of the sheet,
 * [ChatViewModel.onPick], whose rules ([ModelChoice.picked]) cover compare.
 */
class CompareActions internal constructor(
    private val scope: CoroutineScope,
    private val state: StateFlow<ChatUiState>,
    private val choices: ChatChoices,
    private val compares: CompareRepository,
    private val toast: TimedValue<ChatToastUi>,
) {
    private var continuing: Job? = null

    /**
     * The sheet's compare button, the prototype's `toggleCompare` ([ModelChoice.toggledCompare]):
     * compare turns on beside the model, or off dropping the second model; a persona goes either
     * way. The sheet stays open, and its hint says what to pick next.
     */
    fun onToggleCompare() = choices.apply(state.value.choice.toggledCompare())

    /**
     * "continue with this →" on card [side] of the pair [pairId], the prototype's `p.keep`: that
     * card's model answers the conversation's next message alone, with compare off, and the toast
     * says "continuing with gpt-5". The chip follows when the conversation does. The pair stays in
     * the thread as it is. Only a done card offers it, once at a time; when nothing changed, the
     * toast says so.
     */
    fun onContinueWith(pairId: Long, side: Int) {
        val current = state.value
        val conversationId = current.conversationId ?: return
        val pair = current.messages.firstOrNull { it.id == pairId } as? MessageUi.ComparePair ?: return
        val card = pair.sides.getOrNull(side)?.takeIf { it.done } ?: return
        if (continuing?.isActive == true) return
        continuing = scope.launch {
            val continued = try {
                compares.continueWith(conversationId = conversationId, pairMessageId = pairId, side = side)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                false
            }
            toast.show(
                if (continued) {
                    ChatToastUi(FeatureChatRes.string.chat_toast_continuing, listOf(card.modelShortName))
                } else {
                    ChatToastUi(FeatureChatRes.string.chat_toast_continue_failed)
                },
            )
        }
    }
}

/**
 * What answers the chat, as the Model picker changes it: in an open conversation a choice shows at
 * once and is saved with [ChatRepository.choose], and if that changes nothing the chip shows the
 * conversation's own choice again; in a new chat it is kept as the new chat's choice for its first
 * send. A row of the sheet ([ChatViewModel.onPick]) and the compare button
 * ([CompareActions.onToggleCompare]) both go through it.
 *
 * @param generation Moves on "new chat" and on opening another conversation, so a refusal that
 *   comes back after either changes nothing.
 * @param ownChoice The open conversation's own choice, as last observed.
 */
internal class ChatChoices(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ChatUiState>,
    private val repository: ChatRepository,
    private val generation: () -> Int,
    private val ownChoice: () -> ModelChoice?,
) {
    /** The new chat's own pick, or `null` while it follows the default model. */
    var newChatChoice: ModelChoice? = null
        private set

    /** Moves on every choice in an open conversation, so only the latest one is undone when refused. */
    private var picks = 0

    /** [choice] answers from now on, and the accent follows at once. */
    fun apply(choice: ModelChoice) {
        val conversationId = state.value.conversationId
        state.update { it.copy(choice = choice) }
        if (conversationId == null) {
            newChatChoice = choice
            return
        }
        val pick = ++picks
        val pickedFrom = generation()
        scope.launch {
            val chosen = try {
                repository.choose(conversationId, choice)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                false
            }
            // Refused: unless another choice or another conversation came since, show its own again.
            val latest = pick == picks && pickedFrom == generation()
            val own = ownChoice()
            if (!chosen && latest && own != null) state.update { it.copy(choice = own) }
        }
    }

    /** "new chat": the new chat follows the default model again. */
    fun reset() {
        newChatChoice = null
    }
}
