package br.com.weslleycampos.chat.data.chat

import androidx.datastore.preferences.core.preferencesOf
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.PersonaEntity
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalModelRepositoryTest {
    private var database = FakeChatDatabase()
    private var preferences = FakePreferences()

    private fun TestScope.repository() = LocalModelRepository(
        personaDao = database.personaDao,
        preferences = preferences,
        scope = this,
    )

    private val retired = ChatModel(
        id = "gpt-6",
        name = "GPT-6",
        shortName = "gpt-6",
        provider = "OpenAI",
        contextWindow = "1M",
        cost = "$$$",
        hue = 150,
    )

    private fun ids(models: List<ChatModel>) = models.map(ChatModel::id)

    @Test
    fun aFreshInstallSeedsTheThreePresetPersonasInThePrototypesOrder() = runTest {
        val personas = mutableListOf<List<String>>()
        val repository = repository()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observePersonas().collect { list -> personas += list.map { it.id } }
        }

        advanceUntilIdle()

        assertEquals(PersonaPresets.personas, repository.observePersonas().first())
        assertEquals(listOf(listOf("reviewer", "terse", "digest")), personas)
        assertEquals(
            listOf(
                listOf("reviewer", "Code reviewer", "sonnet", "0"),
                listOf("terse", "Terse", "gpt5", "1"),
                listOf("digest", "Paper digest", "gemini", "2"),
            ),
            database.personaRows.value.map { listOf(it.id, it.name, it.modelId, it.position.toString()) },
        )
        assertEquals(PersonaPresets.personas.map { it.prompt }, database.personaRows.value.map { it.prompt })
    }

    @Test
    fun anInstallUpgradedFromVersionTwoGetsThePresetsAndKeepsItsConversations() = runTest {
        val conversations = listOf(
            ConversationEntity(id = 1, title = "Postgres index", modelId = "gpt5", createdAt = 1, updatedAt = 2),
            ConversationEntity(id = 2, title = "useDebounce hook", modelId = "sonnet", createdAt = 3, updatedAt = 4),
        )
        database = FakeChatDatabase(conversations = conversations)

        val personas = repository().observePersonas().first()

        assertEquals(PersonaPresets.personas, personas)
        assertEquals(conversations, database.conversationRows.value)
    }

    @Test
    fun seedingOnEveryStartKeepsThePersonasStoredAndOnlyAddsTheMissingPresets() = runTest {
        val edited = PersonaEntity(
            id = "reviewer",
            name = "Strict reviewer",
            modelId = "opus",
            prompt = "Be strict.",
            position = 0,
        )
        database = FakeChatDatabase(personas = listOf(edited))

        repository().observePersonas().first()
        val secondStart = repository().observePersonas().first()

        assertEquals(2, database.personaInserts)
        assertEquals(listOf("reviewer", "terse", "digest"), secondStart.map { it.id })
        assertEquals("Strict reviewer", secondStart.first().name)
        assertEquals(ModelCatalogue["opus"], secondStart.first().model)
        assertEquals(PersonaPresets.personas.drop(1), secondStart.drop(1))
        assertEquals(3, database.personaRows.value.size)
    }

    @Test
    fun theFavoritesAreSonnetGptFiveAndGeminiUntilTheUserChangesThem() = runTest {
        val favorites = repository().observeFavorites().first()

        assertEquals(listOf("sonnet", "gpt5", "gemini"), ids(favorites))
        assertEquals(ModelRepository.DEFAULT_FAVORITE_IDS, ids(favorites))
        assertEquals(0, preferences.writes)
    }

    @Test
    fun favoritesKeepTheCatalogueOrderWhateverOrderTheyWereAddedIn() = runTest {
        val repository = repository()

        repository.setFavorite(ModelCatalogue["mistral"], favorite = true)
        repository.setFavorite(ModelCatalogue["opus"], favorite = true)
        repository.setFavorite(ModelCatalogue["gpt5"], favorite = false)

        assertEquals(listOf("sonnet", "opus", "gemini", "mistral"), ids(repository.observeFavorites().first()))
    }

    @Test
    fun removingEveryFavoriteLeavesNoneRatherThanTheDefaults() = runTest {
        val repository = repository()

        ModelRepository.DEFAULT_FAVORITE_IDS.forEach { repository.setFavorite(ModelCatalogue[it], favorite = false) }

        assertTrue(repository.observeFavorites().first().isEmpty())
        assertEquals(emptySet(), preferences.stored.value[LocalModelRepository.FavoritesKey])
    }

    @Test
    fun settingAFavoriteToWhatItAlreadyIsChangesNothing() = runTest {
        val repository = repository()
        repository.setFavorite(ModelCatalogue["opus"], favorite = true)
        val writes = preferences.writes

        repository.setFavorite(ModelCatalogue["opus"], favorite = true)
        repository.setFavorite(ModelCatalogue["llama"], favorite = false)

        assertEquals(writes, preferences.writes)
        assertEquals(listOf("sonnet", "opus", "gpt5", "gemini"), ids(repository.observeFavorites().first()))
    }

    @Test
    fun theDefaultModelIsClaudeSonnetUntilAnotherIsSet() = runTest {
        val repository = repository()
        val before = repository.observeDefaultModel().first()

        repository.setDefaultModel(ModelCatalogue["gemini"])

        assertEquals(ModelCatalogue.default, before)
        assertEquals(ModelCatalogue["gemini"], repository.observeDefaultModel().first())
    }

    @Test
    fun aModelOutsideTheCatalogueCanBeNeitherAFavoriteNorTheDefault() = runTest {
        val repository = repository()

        repository.setFavorite(retired, favorite = true)
        repository.setDefaultModel(retired)

        assertEquals(0, preferences.writes)
        assertEquals(ModelCatalogue.default, repository.observeDefaultModel().first())
        assertEquals(ModelRepository.DEFAULT_FAVORITE_IDS, ids(repository.observeFavorites().first()))
    }

    @Test
    fun storedIdsTheCatalogueNoLongerHasReadAsTheDefaultModelAndAreLeftOutOfTheFavorites() = runTest {
        preferences = FakePreferences(
            preferencesOf(
                LocalModelRepository.DefaultModelKey to "gpt-6",
                LocalModelRepository.FavoritesKey to setOf("gpt-6", "o3"),
            ),
        )
        val repository = repository()

        assertEquals(ModelCatalogue.default, repository.observeDefaultModel().first())
        assertEquals(listOf("o3"), ids(repository.observeFavorites().first()))
    }

    @Test
    fun theDefaultModelAndTheFavoritesOnlyEmitWhenThemselvesChange() = runTest {
        val repository = repository()
        val defaults = mutableListOf<String>()
        val favorites = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeDefaultModel().collect { defaults += it.id }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeFavorites().collect { favorites += ids(it) }
        }

        repository.setFavorite(ModelCatalogue["opus"], favorite = true)
        repository.setDefaultModel(ModelCatalogue["o3"])
        repository.setDefaultModel(ModelCatalogue["o3"])
        repository.setFavorite(ModelCatalogue["sonnet"], favorite = true)

        assertEquals(listOf("sonnet", "o3"), defaults)
        assertEquals(listOf(listOf("sonnet", "gpt5", "gemini"), listOf("sonnet", "opus", "gpt5", "gemini")), favorites)
    }
}
