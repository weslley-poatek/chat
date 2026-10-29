package br.com.weslleycampos.chat.feature.chat.picker

import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.ModelRepository
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.ModelVendor
import br.com.weslleycampos.chat.data.chat.models.PersonaPresets
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_title_chat
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_title_default
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelPickerUiStateTest {
    private val sonnet = ModelCatalogue.default
    private val opus = ModelCatalogue["claude-opus-4-1-20250805"]
    private val gemini = ModelCatalogue["gemini-2.5-pro"]
    private val gpt5 = ModelCatalogue["gpt-5"]
    private val o3 = ModelCatalogue["o3"]
    private val llama = ModelCatalogue["llama-4-maverick-17b-128e"]
    private val deepseek = ModelCatalogue["deepseek-v4.1-flash"]
    private val mistral = ModelCatalogue["mistral-large"]

    /** A model Fuelix may list that the catalogue has no design values for, from no vendor the app knows. */
    private val qwen = ModelCatalogue["qwen3-coder"]
    private val favorites = ModelRepository.DEFAULT_FAVORITE_IDS.map(ModelCatalogue::get)
    private val personas = PersonaPresets.personas
    private val reviewer = personas[0]
    private val digest = personas[2]

    private fun chat(
        active: ModelChoice,
        favorites: List<ChatModel> = this.favorites,
        models: List<ChatModel> = ModelCatalogue.models,
    ) = modelPickerUiState(
        variant = ModelPickerVariant.Chat,
        models = models,
        favorites = favorites,
        personas = personas,
        active = active,
    )

    private val ModelPickerUiState.rows: List<ModelPickerRow> get() = favorites + personas + allModels

    private val ModelPickerUiState.marked: List<String>
        get() = rows.filter { it.mark == ModelPickerMark.Active }.map { it.key }

    /** The keys of the rows that carry "◐". */
    private val ModelPickerUiState.compared: List<String>
        get() = rows.filter { it.mark == ModelPickerMark.Compare }.map { it.key }

    /** Each vendor's models, in the sheet's order. */
    private val ModelPickerUiState.byVendor: List<List<ChatModel>>
        get() = vendors.map { vendor -> vendor.rows.map { it.choice.model } }

    /** [model]'s row under "favorites" and under its vendor. */
    private fun bothRows(model: ChatModel) = listOf("favorite:${model.id}", "model:${model.id}")

    @Test
    fun theChatSheetListsTheFavoritesThePersonasAndEveryModelInTheirOrder() {
        val state = chat(ModelChoice(sonnet))

        assertEquals(listOf("Claude Sonnet 4.5", "GPT-5", "Gemini 2.5 Pro"), state.favorites.map { it.name })
        assertEquals(listOf("Code reviewer", "Terse", "Paper digest"), state.personas.map { it.name })
        assertEquals(ModelCatalogue.models.map { it.name }, state.allModels.map { it.name })
        assertTrue(state.showsPersonas)
    }

    // The vendors

    @Test
    fun theModelsAreGroupedByVendorUnderItsLowercaseNameInTheVendorsOrder() {
        val state = chat(ModelChoice(sonnet))

        assertEquals(
            listOf("anthropic", "openai", "google", "meta", "deepseek", "mistral"),
            state.vendors.map { it.label },
        )
        assertEquals(
            listOf(
                listOf(sonnet, opus),
                listOf(gpt5, o3),
                listOf(gemini),
                listOf(llama),
                listOf(deepseek),
                listOf(mistral),
            ),
            state.byVendor,
        )
        assertEquals(state.vendors.flatMap { it.rows }, state.allModels)
    }

    @Test
    fun eachVendorKeepsTheListsOrderAModelOfNoKnownVendorComesLastAndAVendorWithNoneHasNoSection() {
        // As Fuelix may list them: out of the vendors' order, one twice, one from no vendor the app knows.
        val listed = listOf(qwen, o3, opus, gemini, gpt5, sonnet, opus)

        val state = chat(ModelChoice(sonnet), models = listed)

        assertEquals(listOf("anthropic", "openai", "google", "other"), state.vendors.map { it.label })
        assertEquals(listOf(listOf(opus, sonnet), listOf(o3, gpt5), listOf(gemini), listOf(qwen)), state.byVendor)
        assertEquals(ModelVendor.OTHER, qwen.vendor)
    }

    @Test
    fun withNoModelListedThereIsNoVendorSection() {
        val state = chat(ModelChoice(sonnet), models = emptyList())

        assertTrue(state.vendors.isEmpty())
        assertTrue(state.allModels.isEmpty())
        // The favorites and personas are the user's own, listed or not.
        assertEquals(favorites.map { it.name }, state.favorites.map { it.name })
        assertEquals(personas.size, state.personas.size)
    }

    @Test
    fun aFavoriteTheKeyNoLongerListsStaysUnderFavoritesOnly() {
        val listed = listOf(sonnet, gpt5)

        val state = chat(ModelChoice(llama), favorites = listOf(sonnet, llama), models = listed)

        assertEquals(listOf("Claude Sonnet 4.5", "Llama 4 Maverick"), state.favorites.map { it.name })
        assertEquals(listed, state.allModels.map { it.choice.model })
        assertEquals(listOf(true, false), state.allModels.map { it.favorite })
        assertEquals(listOf("favorite:${llama.id}"), state.marked)
    }

    // Stars, picks and marks

    @Test
    fun withNoFavoritesTheFavoritesSectionIsEmptyAndEveryStarIsHollow() {
        val state = chat(ModelChoice(sonnet), favorites = emptyList())

        assertTrue(state.favorites.isEmpty())
        assertEquals(List(ModelCatalogue.models.size) { false }, state.allModels.map { it.favorite })
    }

    @Test
    fun onlyVendorRowsHaveAStarFilledForTheFavorites() {
        val state = chat(ModelChoice(sonnet), favorites = listOf(gemini, llama))

        assertTrue((state.favorites + state.personas).all { it.favorite == null })
        assertEquals(
            listOf(gemini.id, llama.id),
            state.allModels.filter { it.favorite == true }.map { it.choice.model.id },
        )
        assertEquals(ModelCatalogue.models.size - 2, state.allModels.count { it.favorite == false })
    }

    @Test
    fun aModelRowPicksTheModelAloneAndAPersonaRowThePersonaOnItsModel() {
        val state = chat(ModelChoice(reviewer))

        assertEquals(favorites.map { ModelChoice(it) }, state.favorites.map { it.choice })
        assertEquals(ModelCatalogue.models.map { ModelChoice(it) }, state.allModels.map { it.choice })
        assertEquals(personas.map { ModelChoice(model = it.model, persona = it) }, state.personas.map { it.choice })
        assertTrue(state.allModels.all { it.choice.persona == null })
    }

    @Test
    fun theActiveMarkIsOnThePersonaWhenOneAnswersElseOnTheModelsRows() {
        assertEquals(bothRows(sonnet), chat(ModelChoice(sonnet)).marked)
        assertEquals(listOf("model:${llama.id}"), chat(ModelChoice(llama)).marked)
        // Sonnet answers as the persona, so only the persona is marked.
        assertEquals(listOf("persona:reviewer"), chat(ModelChoice(reviewer)).marked)
        assertEquals(listOf("persona:digest"), chat(ModelChoice(digest)).marked)
        assertEquals("●", ModelPickerMark.Active.glyph)
    }

    @Test
    fun theDefaultSheetHasNoPersonasAndMarksTheDefaultModel() {
        val state = modelPickerUiState(
            variant = ModelPickerVariant.Default,
            models = ModelCatalogue.models,
            favorites = favorites,
            personas = personas,
            active = ModelChoice(gemini),
        )

        assertFalse(state.showsPersonas)
        assertTrue(state.personas.isEmpty())
        assertEquals(favorites.map { it.name }, state.favorites.map { it.name })
        assertEquals(ModelCatalogue.models.size, state.allModels.size)
        assertEquals(chat(ModelChoice(gemini)).vendors.map { it.label }, state.vendors.map { it.label })
        assertEquals(bothRows(gemini), state.marked)
    }

    @Test
    fun whileComparingTheSecondModelsRowsCarryTheHalfMarkAndThePrimarysTheDot() {
        val state = chat(ModelChoice(sonnet, compareOn = true, compareWith = gpt5))

        assertEquals(bothRows(sonnet), state.marked)
        assertEquals(bothRows(gpt5), state.compared)
        assertEquals("◐", ModelPickerMark.Compare.glyph)
        // A second model outside the favorites is marked under its vendor only.
        assertEquals(
            listOf("model:${llama.id}"),
            chat(ModelChoice(sonnet, compareOn = true, compareWith = llama)).compared,
        )
        // Compare on with no second model yet, or off with one left over: no half mark.
        assertTrue(chat(ModelChoice(sonnet, compareOn = true)).compared.isEmpty())
        assertTrue(chat(ModelChoice(sonnet, compareOn = false, compareWith = gpt5)).compared.isEmpty())
    }

    @Test
    fun theCompareButtonIsOnWhileComparingAndTheHintSaysWhatToPickNext() {
        val off = chat(ModelChoice(sonnet))
        val picking = chat(ModelChoice(sonnet, compareOn = true))
        val comparing = chat(ModelChoice(sonnet, compareOn = true, compareWith = gpt5))

        assertTrue(off.showsCompare)
        assertEquals(listOf(false, true, true), listOf(off, picking, comparing).map { it.compareOn })
        assertNull(off.compareHint)
        assertEquals("pick a second model to run beside sonnet-4.5", picking.compareHint)
        assertEquals("sonnet-4.5 ⇄ gpt-5 · tap another model to swap the second", comparing.compareHint)
        assertEquals("pick a second model to run beside llama-4", compareHint(ModelChoice(llama, compareOn = true)))
        assertNull(compareHint(ModelChoice(sonnet, compareWith = gpt5)))
    }

    @Test
    fun theDefaultSheetHasNoCompareButtonHintOrHalfMark() {
        val state = modelPickerUiState(
            variant = ModelPickerVariant.Default,
            models = ModelCatalogue.models,
            favorites = favorites,
            personas = personas,
            active = ModelChoice(sonnet, compareOn = true, compareWith = gpt5),
        )

        assertFalse(state.showsCompare)
        assertFalse(state.compareOn)
        assertNull(state.compareHint)
        assertTrue(state.compared.isEmpty())
        assertEquals(bothRows(sonnet), state.marked)
    }

    @Test
    fun theSheetIsTitledModelForTheChatAndDefaultModelForSettings() {
        assertEquals(FeatureChatRes.string.model_picker_title_chat, ModelPickerVariant.Chat.title)
        assertEquals(FeatureChatRes.string.model_picker_title_default, ModelPickerVariant.Default.title)
    }

    // Meta and dots

    @Test
    fun rowMetaIsTheIdContextAndCostOrThePersonasModelAndPrompt() {
        assertEquals("claude-sonnet-4-5-20250929 · 200k ctx · $$", modelRowMeta(sonnet))
        assertEquals("llama-4-maverick-17b-128e · 1M ctx · $", modelRowMeta(llama))
        assertEquals(
            "→ sonnet-4.5 · Correctness first, style last. Cite line numbers. No praise.",
            personaRowMeta(reviewer),
        )
        assertEquals("→ gemini-2.5-pro · Claim, method, evidence, caveats. Bullets.", personaRowMeta(digest))

        val state = chat(ModelChoice(sonnet))
        assertEquals(state.allModels.map { modelRowMeta(it.choice.model) }, state.allModels.map { it.meta })
        assertEquals(personas.map(::personaRowMeta), state.personas.map { it.meta })
    }

    @Test
    fun rowMetaLeavesOutAContextWindowOrCostTheAppDoesNotKnow() {
        val mini = ChatModel(
            id = "gpt-5-mini",
            name = "GPT-5 Mini",
            shortName = "gpt-5-mini",
            vendor = ModelVendor.OPENAI,
            contextWindow = "400k",
        )

        assertEquals("deepseek-v4.1-flash · $", modelRowMeta(deepseek))
        assertEquals("gpt-5-mini · 400k ctx", modelRowMeta(mini))
        assertEquals("qwen3-coder", modelRowMeta(qwen))
        assertEquals("qwen3-coder", chat(ModelChoice(sonnet), models = listOf(qwen)).allModels.single().meta)
    }

    @Test
    fun eachRowsDotIsItsVendorsPaletteAndAPersonasIsItsModels() {
        val state = chat(ModelChoice(sonnet))

        assertEquals(
            listOf(ChatPalette.SONNET, ChatPalette.GPT5, ChatPalette.GEMINI),
            state.personas.map { it.palette },
        )
        assertEquals(
            listOf(
                ChatPalette.SONNET,
                ChatPalette.SONNET,
                ChatPalette.GPT5,
                ChatPalette.GPT5,
                ChatPalette.GEMINI,
                ChatPalette.LLAMA,
                ChatPalette.DEEPSEEK,
                ChatPalette.MISTRAL,
            ),
            state.allModels.map { it.palette },
        )
        assertEquals(ChatPalette.Default, chat(ModelChoice(sonnet), models = listOf(qwen)).allModels.single().palette)
    }

    @Test
    fun rowKeysAreUniqueAcrossTheSheet() {
        val keys = chat(ModelChoice(sonnet), models = ModelCatalogue.models + qwen).rows.map { it.key }

        assertEquals(keys.size, keys.toSet().size)
        assertNull(keys.firstOrNull { it.isBlank() })
    }

    @Test
    fun previewsCoverEveryVariantInLightAndDark() {
        val cases = ModelPickerPreviewParameterProvider().values.toList()
        val names = listOf(
            "Chat",
            "Chat · persona",
            "Chat · no favorites",
            "Chat · compare",
            "Chat · compare · second",
            "Default",
        )

        assertEquals(names + names, cases.map { it.name })
        assertEquals(List(names.size) { false } + List(names.size) { true }, cases.map { it.darkTheme })
        val states = cases.take(names.size).associateBy { it.name }
        assertEquals(bothRows(sonnet), states.getValue("Chat").state.marked)
        assertEquals(listOf("persona:reviewer"), states.getValue("Chat · persona").state.marked)
        assertTrue(states.getValue("Chat · no favorites").state.favorites.isEmpty())
        val picking = states.getValue("Chat · compare").state
        assertTrue(picking.compareOn && picking.compared.isEmpty())
        assertEquals("pick a second model to run beside sonnet-4.5", picking.compareHint)
        val second = states.getValue("Chat · compare · second").state
        assertEquals(bothRows(gpt5), second.compared)
        assertEquals(ChatPalette.SONNET, states.getValue("Chat · compare · second").palette)
        val default = states.getValue("Default")
        assertEquals(ModelPickerVariant.Default, default.state.variant)
        assertTrue(default.state.personas.isEmpty())
        // Each case lists the catalogue's models by vendor, and wears the accent of what answers.
        assertTrue(cases.all { it.state.allModels.size == ModelCatalogue.models.size })
        assertEquals(ChatPalette.GPT5, default.palette)
        assertEquals(ChatPalette.SONNET, states.getValue("Chat · persona").palette)
    }
}
