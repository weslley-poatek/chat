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
    fun theRootWearsTheDefaultModelsPaletteAndFollowsItAtOnce() = runTest {
        val defaultModel = MutableStateFlow(ModelCatalogue["gemini"])
        val viewModel = AppViewModel(DefaultModelOnly(defaultModel))
        runCurrent()
        assertEquals(ChatPalette.GEMINI, viewModel.palette.value)

        for (model in ModelCatalogue.models) {
            defaultModel.value = model
            runCurrent()
            assertEquals(model.id.uppercase(), viewModel.palette.value.name, model.id)
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
    override fun observeFavorites(): Flow<List<ChatModel>> = emptyFlow()

    override suspend fun setFavorite(model: ChatModel, favorite: Boolean) = Unit

    override fun observePersonas(): Flow<List<Persona>> = emptyFlow()

    override fun observeDefaultModel(): Flow<ChatModel> = defaultModel

    override suspend fun setDefaultModel(model: ChatModel) = Unit
}
