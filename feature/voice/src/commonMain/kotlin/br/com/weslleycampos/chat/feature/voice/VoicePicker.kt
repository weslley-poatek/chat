package br.com.weslleycampos.chat.feature.voice

import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.VoiceRepository
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The model chip's sheet on Voice, the prototype's `openModelSheet` and `mRow` (lines 848–853) on
 * the chat sheet with no compare button: voice never compares.
 *
 * A pick ([onPick]) answers from now on: the chip and the accent follow at once and the sheet
 * closes. It is one call, [VoiceRepository.choose], which sets the conversation's choice and the
 * default model for new sessions and new chats. Before a new session's first transcript there is no
 * conversation yet, so the pick is also kept as [pending], which the first transcript starts the
 * conversation on (`saveTranscript(null, newSession = pending)`); the default alone cannot hold a
 * persona. A pick that changes nothing puts the chip back on what answers.
 *
 * @property sessionChoice What the session last read answers with, or `null` before it is read.
 */
class VoicePicker internal constructor(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<VoiceUiState>,
    private val voices: VoiceRepository,
    private val models: ModelRepository,
    private val sessionChoice: () -> ModelChoice?,
) {
    /** A new session's pick before its first transcript, which that transcript starts on; else `null`. */
    var pending: ModelChoice? = null
        private set

    /** The model chip: the sheet opens. */
    fun onOpen() = state.update { it.copy(modelPickerOpen = true) }

    /** A tap on the scrim, a drag down or Back: the sheet closes, picking nothing. */
    fun onDismiss() = state.update { it.copy(modelPickerOpen = false) }

    /**
     * A row of the sheet, [row] being what it picks: a model alone or a persona on its model
     * ([ModelChoice.picked], with compare never on). The sheet closes, the chip and the accent follow
     * at once, and the choice is saved with [VoiceRepository.choose].
     */
    fun onPick(row: ModelChoice) {
        val shown = state.value
        val choice = shown.choice.picked(row) ?: return
        val conversationId = shown.conversationId
        val before = pending
        if (conversationId == null) pending = choice
        state.update { it.copy(modelPickerOpen = false, choice = choice) }
        scope.launch {
            val chosen = try {
                voices.choose(conversationId, choice)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // The prototype has no failure to show; the chip goes back to what answers.
                false
            }
            if (!chosen && state.value.conversationId == conversationId) {
                if (conversationId == null && pending == choice) pending = before
                val answers = pending.takeIf { conversationId == null } ?: sessionChoice() ?: shown.choice
                state.update { it.copy(choice = answers) }
            }
        }
    }

    /**
     * A row's "★" / "☆", the prototype's `mRow.fav`: stars or unstars [model] without picking it;
     * the sheet stays open and the stars follow the stored favorites.
     */
    fun onToggleFavorite(model: ChatModel) {
        val favorite = state.value.favorites.none { it.id == model.id }
        scope.launch {
            try {
                models.setFavorite(model, favorite)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                // Nothing was stored, so the star keeps showing what is.
            }
        }
    }

    /** The first transcript was saved on [pending]: the conversation holds the choice from now on. */
    internal fun onFirstTranscript() {
        pending = null
    }

    /** "end": a fresh session starts on the default model, with no persona, as a new chat does. */
    internal fun reset() {
        pending = null
        state.update { it.copy(modelPickerOpen = false) }
    }
}
