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
import br.com.weslleycampos.chat.data.fuelix.FuelixClient
import br.com.weslleycampos.chat.data.fuelix.FuelixException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * [ModelRepository] on this device: personas are rows of Room's `personas` table, and the favorites,
 * the default model and the key's models, as [fuelix] last listed them, are plain preferences in the
 * app's unencrypted DataStore.
 *
 * Ids stored before Fuelix read as the models they became ([ModelCatalogue.resolve]); the favorites
 * are stored resolved the next time they change.
 *
 * One instance per process. When it is created it seeds [PersonaPresets] into the table, inserting
 * only the presets whose id is missing, so a fresh install, an install upgraded from version 2 and
 * every later start run the same step and end with the same rows. Persona reads wait for it.
 */
internal class LocalModelRepository(
    private val personaDao: PersonaDao,
    private val preferences: DataStore<Preferences>,
    private val fuelix: FuelixClient,
    scope: CoroutineScope,
) : ModelRepository {
    private val seeding: Deferred<Unit> = scope.async {
        val presets = PersonaPresets.personas.mapIndexed { position, persona -> persona.toEntity(position) }
        personaDao.insertIfAbsent(presets)
    }

    override fun observeModels(): Flow<List<ChatModel>> = preferences.data
        .map { stored -> listedModels(stored) }
        .distinctUntilChanged()

    override suspend fun refreshModels(): Boolean {
        val ids = try {
            fuelix.models().filter(ModelCatalogue::isChatModel).distinct()
        } catch (ignored: FuelixException) {
            return false
        }
        if (ids.isEmpty()) return false
        preferences.edit { it[ModelIdsKey] = ids.joinToString(MODEL_ID_SEPARATOR) }
        return true
    }

    override fun observeFavorites(): Flow<List<ChatModel>> = preferences.data
        .map { stored ->
            val order = listedModels(stored).map(ChatModel::id)
            favoriteIds(stored)
                .sortedWith(compareBy({ order.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }, { it }))
                .map(ModelCatalogue::of)
        }
        .distinctUntilChanged()

    override suspend fun setFavorite(model: ChatModel, favorite: Boolean) {
        val id = ModelCatalogue.find(model.id)?.id ?: return
        preferences.edit { stored ->
            val ids = favoriteIds(stored)
            stored[FavoritesKey] = if (favorite) ids + id else ids - id
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
        val id = ModelCatalogue.find(model.id)?.id ?: return
        preferences.edit { it[DefaultModelKey] = id }
    }

    /** The key's models as last listed, or the catalogue's while none were. */
    private fun listedModels(stored: Preferences): List<ChatModel> =
        stored[ModelIdsKey]
            ?.split(MODEL_ID_SEPARATOR)
            ?.filter(String::isNotBlank)
            ?.map(ModelCatalogue::of)
            ?.takeIf(List<ChatModel>::isNotEmpty)
            ?: ModelCatalogue.models

    /** The stored favorites, resolved, or the prototype's while none were ever stored. */
    private fun favoriteIds(stored: Preferences): Set<String> =
        (stored[FavoritesKey] ?: ModelRepository.DEFAULT_FAVORITE_IDS).mapTo(linkedSetOf(), ModelCatalogue::resolve)

    companion object {
        /** The favorite model ids. Absent until the first change; an empty set means no favorites. */
        val FavoritesKey: Preferences.Key<Set<String>> = stringSetPreferencesKey("favorite_model_ids")

        /** The default model's id. Absent until it is first set. */
        val DefaultModelKey: Preferences.Key<String> = stringPreferencesKey("default_model_id")

        /** The key's chat model ids, one per line, as Fuelix last listed them. Absent until first read. */
        val ModelIdsKey: Preferences.Key<String> = stringPreferencesKey("fuelix_model_ids")

        private const val MODEL_ID_SEPARATOR = "\n"
    }
}
