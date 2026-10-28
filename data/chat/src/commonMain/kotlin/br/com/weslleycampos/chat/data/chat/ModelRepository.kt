package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import kotlinx.coroutines.flow.Flow

/**
 * What the model picker lists beside [ModelCatalogue]: the user's favorite models, their personas,
 * and the default model for new chats. Everything is kept on this device and survives restarts.
 * Every read is a [Flow] that emits the current value when collected and again on every change that
 * alters it, never the same value twice in a row.
 *
 * Which model and persona answer a conversation is the conversation's own:
 * [ChatRepository.choose] changes it, and [ChatRepository.send] takes a new chat's.
 */
interface ModelRepository {
    /**
     * The favorite models in [ModelCatalogue] order, whatever order they were added in: the picker's
     * "favorites". [DEFAULT_FAVORITE_IDS] until the user changes them; empty once every one is
     * removed.
     */
    fun observeFavorites(): Flow<List<ChatModel>>

    /**
     * Adds [model] to the favorites, or removes it, as the picker's "★" / "☆" does. Does nothing for
     * a model the catalogue does not have, or when [model] already is or is not a favorite.
     */
    suspend fun setFavorite(model: ChatModel, favorite: Boolean)

    /**
     * The personas in the picker's order: [PersonaPresets] on every install, which are seeded into the
     * database the first time this repository starts, then any the user has.
     */
    fun observePersonas(): Flow<List<Persona>>

    /**
     * The model a new chat and a new voice session start on, and the accent of every screen that
     * shows no conversation: [ModelCatalogue.default] until [setDefaultModel] picks another.
     */
    fun observeDefaultModel(): Flow<ChatModel>

    /**
     * Makes [model] the default for new chats and new voice sessions, as a pick on the Voice screen
     * does ([VoiceRepository.choose]). Does nothing for a model the catalogue does not have.
     */
    suspend fun setDefaultModel(model: ChatModel)

    companion object {
        /** The prototype's initial `favs`: Claude Sonnet 4.5, GPT-5 and Gemini 2.5 Pro. */
        val DEFAULT_FAVORITE_IDS: List<String> = listOf("sonnet", "gpt5", "gemini")
    }
}
