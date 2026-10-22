package br.com.weslleycampos.chat.data.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The favorites and the default model in a real preferences file, read back by a new DataStore on
 * that file as the next app start does.
 */
class ModelPreferencesPersistenceTest {
    private val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "chat-models-${Random.nextLong()}.preferences_pb"

    @AfterTest
    fun deleteFile() {
        FileSystem.SYSTEM.delete(path)
    }

    /** Runs [block] on a repository over the file, then closes its DataStore, as the process ending does. */
    private suspend fun <T> onAppStart(block: suspend (ModelRepository) -> T): T {
        val job = Job()
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.Default + job),
            produceFile = { path },
        )
        try {
            return block(
                LocalModelRepository(
                    personaDao = FakeChatDatabase().personaDao,
                    preferences = store,
                    scope = CoroutineScope(Dispatchers.Default + job),
                ),
            )
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun theFavoritesAndTheDefaultModelSurviveARestart() = runTest {
        onAppStart { repository ->
            repository.setFavorite(ModelCatalogue["opus"], favorite = true)
            repository.setFavorite(ModelCatalogue["gpt5"], favorite = false)
            repository.setDefaultModel(ModelCatalogue["gemini"])
        }

        val (favorites, default) = onAppStart { repository ->
            repository.observeFavorites().first().map { it.id } to repository.observeDefaultModel().first()
        }

        assertEquals(listOf("sonnet", "opus", "gemini"), favorites)
        assertEquals(ModelCatalogue["gemini"], default)
    }

    @Test
    fun noFavoritesLeftIsStillNoneAfterARestart() = runTest {
        onAppStart { repository ->
            ModelRepository.DEFAULT_FAVORITE_IDS.forEach { id ->
                repository.setFavorite(ModelCatalogue[id], favorite = false)
            }
        }

        val favorites = onAppStart { repository -> repository.observeFavorites().first() }

        assertTrue(favorites.isEmpty())
    }
}
