package br.com.weslleycampos.chat.data.chat

import androidx.datastore.preferences.core.preferencesOf
import br.com.weslleycampos.chat.core.database.entities.ConversationEntity
import br.com.weslleycampos.chat.core.database.entities.PersonaEntity
import br.com.weslleycampos.chat.data.chat.fakes.FakeChatDatabase
import br.com.weslleycampos.chat.data.chat.fakes.FakeFuelixClient
import br.com.weslleycampos.chat.data.chat.fakes.FakePreferences
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.data.fuelix.FuelixException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalModelRepositoryTest {
    private var database = FakeChatDatabase()
    private var preferences = FakePreferences()
    private val fuelix = FakeFuelixClient()

    private fun TestScope.repository() = LocalModelRepository(
        personaDao = database.personaDao,
        preferences = preferences,
        fuelix = fuelix,
        scope = this,
    )

    private val sonnet = ModelCatalogue.DEFAULT_MODEL_ID
    private val opus = "claude-opus-4-1-20250805"
    private val haiku = "claude-haiku-4-5-20251001"

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
                listOf("reviewer", "Code reviewer", sonnet, "0"),
                listOf("terse", "Terse", "gpt-5", "1"),
                listOf("digest", "Paper digest", "gemini-2.5-pro", "2"),
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
            modelId = opus,
            prompt = "Be strict.",
            position = 0,
        )
        database = FakeChatDatabase(personas = listOf(edited))

        repository().observePersonas().first()
        val secondStart = repository().observePersonas().first()

        assertEquals(2, database.personaInserts)
        assertEquals(listOf("reviewer", "terse", "digest"), secondStart.map { it.id })
        assertEquals("Strict reviewer", secondStart.first().name)
        assertEquals(ModelCatalogue[opus], secondStart.first().model)
        assertEquals(PersonaPresets.personas.drop(1), secondStart.drop(1))
        assertEquals(3, database.personaRows.value.size)
    }

    @Test
    fun untilTheKeysModelsAreReadTheCatalogueIsListed() = runTest {
        val repository = repository()

        assertSame(ModelCatalogue.models, repository.observeModels().first())
        assertEquals(0, fuelix.modelsCalls)
    }

    @Test
    fun refreshingListsTheKeysChatModelsInFuelixsOrderOnceEach() = runTest {
        val repository = repository()
        fuelix.models = listOf(
            "gpt-5",
            "text-embedding-3-large",
            haiku,
            "whisper-1",
            "gpt-5",
            "claude-sonnet-4-5-anthropic",
            "o3",
        )

        assertTrue(repository.refreshModels())

        val models = repository.observeModels().first()
        assertEquals(listOf("gpt-5", haiku, "o3"), ids(models))
        assertSame(ModelCatalogue.models[2], models[0])
        assertEquals(ModelCatalogue.of(haiku), models[1])
        assertEquals("gpt-5\n$haiku\no3", preferences.stored.value[LocalModelRepository.ModelIdsKey])
        assertEquals(ids(models), ids(repository().observeModels().first()))
    }

    @Test
    fun aRefreshThatFailsOrFindsNoChatModelKeepsTheListAsItWas() = runTest {
        val repository = repository()
        fuelix.modelsFailure = FuelixException.Unreachable(cause = IllegalStateException("offline"))

        assertFalse(repository.refreshModels())
        assertSame(ModelCatalogue.models, repository.observeModels().first())
        assertEquals(0, preferences.writes)

        fuelix.modelsFailure = null
        fuelix.models = listOf("gpt-5", haiku)
        assertTrue(repository.refreshModels())
        val writes = preferences.writes
        fuelix.modelsFailure = FuelixException.KeyRefused()
        assertFalse(repository.refreshModels())
        fuelix.modelsFailure = null
        fuelix.models = listOf("whisper-1", "text-embedding-3-large")
        assertFalse(repository.refreshModels())
        fuelix.models = emptyList()
        assertFalse(repository.refreshModels())

        assertEquals(listOf("gpt-5", haiku), ids(repository.observeModels().first()))
        assertEquals(writes, preferences.writes)
        assertEquals(5, fuelix.modelsCalls)
    }

    @Test
    fun aBlankStoredListReadsAsTheCatalogue() = runTest {
        preferences = FakePreferences(preferencesOf(LocalModelRepository.ModelIdsKey to "\n \n"))

        assertSame(ModelCatalogue.models, repository().observeModels().first())
    }

    @Test
    fun theModelsOnlyEmitWhenTheListChanges() = runTest {
        val repository = repository()
        val lists = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeModels().collect { lists += ids(it) }
        }

        fuelix.models = listOf("gpt-5", haiku)
        repository.refreshModels()
        repository.refreshModels()
        repository.setFavorite(ModelCatalogue[opus], favorite = true)
        repository.setDefaultModel(ModelCatalogue["o3"])
        fuelix.models = listOf(haiku)
        repository.refreshModels()

        assertEquals(listOf(ids(ModelCatalogue.models), listOf("gpt-5", haiku), listOf(haiku)), lists)
    }

    @Test
    fun theFavoritesAreSonnetGptFiveAndGeminiUntilTheUserChangesThem() = runTest {
        val favorites = repository().observeFavorites().first()

        assertEquals(listOf(sonnet, "gpt-5", "gemini-2.5-pro"), ids(favorites))
        assertEquals(ModelRepository.DEFAULT_FAVORITE_IDS, ids(favorites))
        assertEquals(0, preferences.writes)
    }

    @Test
    fun favoritesKeepTheListedOrderWhateverOrderTheyWereAddedIn() = runTest {
        val repository = repository()

        repository.setFavorite(ModelCatalogue["mistral-large"], favorite = true)
        repository.setFavorite(ModelCatalogue[opus], favorite = true)
        repository.setFavorite(ModelCatalogue["gpt-5"], favorite = false)

        assertEquals(
            listOf(sonnet, opus, "gemini-2.5-pro", "mistral-large"),
            ids(repository.observeFavorites().first()),
        )
    }

    @Test
    fun favoritesFollowTheKeysOrderThenAnyNoLongerListedByTheirIds() = runTest {
        val repository = repository()
        repository.setFavorite(ModelCatalogue["o3"], favorite = true)
        fuelix.models = listOf("gemini-2.5-pro", "gpt-5", opus)

        repository.refreshModels()

        assertEquals(listOf("gemini-2.5-pro", "gpt-5", sonnet, "o3"), ids(repository.observeFavorites().first()))
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
        repository.setFavorite(ModelCatalogue[opus], favorite = true)
        val writes = preferences.writes

        repository.setFavorite(ModelCatalogue[opus], favorite = true)
        repository.setFavorite(ModelCatalogue["llama-4-maverick-17b-128e"], favorite = false)

        assertEquals(writes, preferences.writes)
        assertEquals(listOf(sonnet, opus, "gpt-5", "gemini-2.5-pro"), ids(repository.observeFavorites().first()))
    }

    @Test
    fun theDefaultModelIsClaudeSonnetUntilAnotherIsSet() = runTest {
        val repository = repository()
        val before = repository.observeDefaultModel().first()

        repository.setDefaultModel(ModelCatalogue["gemini-2.5-pro"])

        assertEquals(ModelCatalogue.default, before)
        assertEquals(ModelCatalogue["gemini-2.5-pro"], repository.observeDefaultModel().first())
    }

    @Test
    fun anyModelTheKeyListsCanBeAFavoriteOrTheDefault() = runTest {
        val repository = repository()

        repository.setFavorite(ModelCatalogue.of(haiku), favorite = true)
        repository.setDefaultModel(ModelCatalogue.of(haiku))

        assertEquals(ModelCatalogue.of(haiku), repository.observeDefaultModel().first())
        assertEquals(ModelRepository.DEFAULT_FAVORITE_IDS + haiku, ids(repository.observeFavorites().first()))
    }

    @Test
    fun aModelWithABlankIdCanBeNeitherAFavoriteNorTheDefault() = runTest {
        val repository = repository()
        val blank = ModelCatalogue.of(" ")

        repository.setFavorite(blank, favorite = true)
        repository.setDefaultModel(blank)

        assertEquals(0, preferences.writes)
        assertEquals(ModelCatalogue.default, repository.observeDefaultModel().first())
        assertEquals(ModelRepository.DEFAULT_FAVORITE_IDS, ids(repository.observeFavorites().first()))
    }

    @Test
    fun idsStoredBeforeFuelixReadAsTheModelsTheyBecameAndAreStoredResolvedOnTheNextChange() = runTest {
        preferences = FakePreferences(
            preferencesOf(
                LocalModelRepository.DefaultModelKey to "gemini",
                LocalModelRepository.FavoritesKey to setOf("gpt-6", "opus", "o3"),
            ),
        )
        val repository = repository()

        assertEquals(ModelCatalogue["gemini-2.5-pro"], repository.observeDefaultModel().first())
        assertEquals(listOf(opus, "o3", "gpt-6"), ids(repository.observeFavorites().first()))

        repository.setFavorite(ModelCatalogue.default, favorite = true)

        assertEquals(setOf("gpt-6", opus, "o3", sonnet), preferences.stored.value[LocalModelRepository.FavoritesKey])
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

        repository.setFavorite(ModelCatalogue[opus], favorite = true)
        repository.setDefaultModel(ModelCatalogue["o3"])
        repository.setDefaultModel(ModelCatalogue["o3"])
        repository.setFavorite(ModelCatalogue.default, favorite = true)

        assertEquals(listOf(sonnet, "o3"), defaults)
        assertEquals(
            listOf(listOf(sonnet, "gpt-5", "gemini-2.5-pro"), listOf(sonnet, opus, "gpt-5", "gemini-2.5-pro")),
            favorites,
        )
    }
}
