package br.com.weslleycampos.chat.data.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import br.com.weslleycampos.chat.core.database.daos.PersonaDao
import br.com.weslleycampos.chat.core.database.entities.PersonaEntity
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * [ModelRepository] on this device: personas are rows of Room's `personas` table, and the favorites
 * and default model are plain preferences in the app's unencrypted DataStore.
 *
 * One instance per process. When it is created it seeds [PersonaPresets] into the table, inserting
 * only the presets whose id is missing, so a fresh install, an install upgraded from version 2 and
 * every later start run the same step and end with the same rows. Persona reads wait for it.
 */
internal class LocalModelRepository(
    private val personaDao: PersonaDao,
    private val preferences: DataStore<Preferences>,
    scope: CoroutineScope,
) : ModelRepository {
    private val seeding: Deferred<Unit> = scope.async {
        val presets = PersonaPresets.personas.mapIndexed { position, persona -> persona.toEntity(position) }
        personaDao.insertIfAbsent(presets)
    }

    override fun observeFavorites(): Flow<List<ChatModel>> = preferences.data
        .map { stored -> favoriteIds(stored).let { ids -> ModelCatalogue.models.filter { it.id in ids } } }
        .distinctUntilChanged()

    override suspend fun setFavorite(model: ChatModel, favorite: Boolean) {
        if (ModelCatalogue.find(model.id) == null) return
        preferences.edit { stored ->
            val ids = favoriteIds(stored)
            stored[FavoritesKey] = if (favorite) ids + model.id else ids - model.id
        }
    }

    override fun observePersonas(): Flow<List<Persona>> = flow {
        seeding.await()
        emitAll(personaDao.observeAll().map { rows -> rows.map(PersonaEntity::toPersona) }.distinctUntilChanged())
    }

    override fun observeDefaultModel(): Flow<ChatModel> = preferences.data
        .map { stored -> stored[DefaultModelKey]?.let(ModelCatalogue::find) ?: ModelCatalogue.default }
        .distinctUntilChanged()

    override suspend fun setDefaultModel(model: ChatModel) {
        if (ModelCatalogue.find(model.id) == null) return
        preferences.edit { it[DefaultModelKey] = model.id }
    }

    /** The stored favorites, or the prototype's while none were ever stored. */
    private fun favoriteIds(stored: Preferences): Set<String> =
        stored[FavoritesKey] ?: ModelRepository.DEFAULT_FAVORITE_IDS.toSet()

    companion object {
        /** The favorite model ids. Absent until the first change; an empty set means no favorites. */
        val FavoritesKey: Preferences.Key<Set<String>> = stringSetPreferencesKey("favorite_model_ids")

        /** The default model's id. Absent until it is first set. */
        val DefaultModelKey: Preferences.Key<String> = stringPreferencesKey("default_model_id")
    }
}
