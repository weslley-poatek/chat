package br.com.weslleycampos.chat

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.Persona
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The root accent: the default model's palette, at once on every change. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theRootWearsTheDefaultModelsVendorsPaletteAndFollowsItAtOnce() = runTest {
        val defaultModel = MutableStateFlow(ModelCatalogue["gemini-2.5-pro"])
        val viewModel = AppViewModel(DefaultModelOnly(defaultModel))
        runCurrent()
        assertEquals(ChatPalette.GEMINI, viewModel.palette.value)

        val palettes = mapOf(
            "claude-sonnet-4-5-20250929" to ChatPalette.SONNET,
            "claude-opus-4-1-20250805" to ChatPalette.SONNET,
            "gpt-5" to ChatPalette.GPT5,
            "o3" to ChatPalette.GPT5,
            "gemini-2.5-pro" to ChatPalette.GEMINI,
            "llama-4-maverick-17b-128e" to ChatPalette.LLAMA,
            "deepseek-v4.1-flash" to ChatPalette.DEEPSEEK,
            "mistral-large" to ChatPalette.MISTRAL,
            // A model of no known vendor, as Fuelix may list one.
            "qwen3-coder" to ChatPalette.Default,
        )
        for ((id, palette) in palettes) {
            defaultModel.value = ModelCatalogue[id]
            runCurrent()
            assertEquals(palette, viewModel.palette.value, id)
        }
    }

    @Test
    fun untilTheDefaultModelIsReadTheRootWearsTheDefaultPalette() = runTest {
        val viewModel = AppViewModel(DefaultModelOnly(defaultModel = emptyFlow()))
        runCurrent()

        assertEquals(ChatPalette.Default, viewModel.palette.value)
    }
}

/** A [ModelRepository] with only a default model: the root reads nothing else. */
private class DefaultModelOnly(private val defaultModel: Flow<ChatModel>) : ModelRepository {
    override fun observeModels(): Flow<List<ChatModel>> = emptyFlow()

    override suspend fun refreshModels(): Boolean = false

    override fun observeFavorites(): Flow<List<ChatModel>> = emptyFlow()

    override suspend fun setFavorite(model: ChatModel, favorite: Boolean) = Unit

    override fun observePersonas(): Flow<List<Persona>> = emptyFlow()

    override fun observeDefaultModel(): Flow<ChatModel> = defaultModel

    override suspend fun setDefaultModel(model: ChatModel) = Unit
}
