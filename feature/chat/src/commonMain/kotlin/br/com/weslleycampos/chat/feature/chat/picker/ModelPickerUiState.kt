package br.com.weslleycampos.chat.feature.chat.picker

import androidx.compose.runtime.Immutable
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.feature.chat.MetaSeparator
import br.com.weslleycampos.chat.feature.chat.palette
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_title_chat
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_title_default
import org.jetbrains.compose.resources.StringResource

/** Which sheet the Model picker frame is: the prototype's `sheet`. */
enum class ModelPickerVariant(val title: StringResource) {
    /** `sheet:'chat'`, from the model chip: "Model", what answers the chat, personas included. */
    Chat(FeatureChatRes.string.model_picker_title_chat),

    /**
     * `sheet:'default'`, from Settings' "Default model" (row 10): "Default model", the model new
     * chats start on. A persona is not a default, so it lists no personas.
     */
    Default(FeatureChatRes.string.model_picker_title_default),
}

/** A row's right-hand mark, mono 12 in the accent. Row 7 adds compare's "◐". */
enum class ModelPickerMark(val glyph: String) {
    /** The model or persona that answers, the prototype's "●". */
    Active("●"),
}

/**
 * One row of the sheet, the prototype's `mRow`.
 *
 * @property key Unique in the sheet: a model is listed under "favorites" and again under "all models".
 * @property choice What a tap on the row picks: the model alone, or the persona on its model.
 * @property meta "Anthropic · 200k ctx · $$", or "→ sonnet-4.5 · Correctness first, …" for a persona.
 * @property palette The 8 dp dot's colour: the model's accent, a persona's model's for a persona.
 * @property favorite "★" (`true`) or "☆" (`false`) on an "all models" row; `null` elsewhere, no star.
 * @property mark "●" on what answers, else `null`.
 */
@Immutable
data class ModelPickerRow(
    val key: String,
    val choice: ModelChoice,
    val name: String,
    val meta: String,
    val palette: ChatPalette,
    val favorite: Boolean?,
    val mark: ModelPickerMark?,
)

/**
 * Everything the Model picker sheet lists, in its order: "favorites", "personas" (with "+ new
 * persona") on the chat sheet only, then "all models".
 */
@Immutable
data class ModelPickerUiState(
    val variant: ModelPickerVariant,
    val favorites: List<ModelPickerRow>,
    val personas: List<ModelPickerRow>,
    val allModels: List<ModelPickerRow>,
) {
    /** The "personas" section and "+ new persona": the chat sheet's only. */
    val showsPersonas: Boolean get() = variant == ModelPickerVariant.Chat
}

/**
 * The sheet for [variant], marking [active]: the chat's [ModelChoice] on the chat sheet, or
 * `ModelChoice(defaultModel)` on the default one. [favorites] come in [ModelCatalogue] order, as
 * `ModelRepository.observeFavorites` gives them, and [personas] in theirs; the default sheet
 * leaves the personas out.
 *
 * The "●" goes on the persona that answers, or, with none, on every row of the model that does,
 * the prototype's `active` in `mRow`.
 */
fun modelPickerUiState(
    variant: ModelPickerVariant,
    favorites: List<ChatModel>,
    personas: List<Persona>,
    active: ModelChoice,
): ModelPickerUiState {
    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
    fun modelRow(section: String, model: ChatModel, star: Boolean) = ModelPickerRow(
        key = "$section:${model.id}",
        choice = ModelChoice(model),
        name = model.name,
        meta = modelRowMeta(model),
        palette = model.palette,
        favorite = if (star) model.id in favoriteIds else null,
        mark = ModelPickerMark.Active.takeIf { active.persona == null && active.model.id == model.id },
    )
    val personaRows = if (variant == ModelPickerVariant.Chat) {
        personas.map { persona ->
            ModelPickerRow(
                key = "persona:${persona.id}",
                choice = ModelChoice(persona),
                name = persona.name,
                meta = personaRowMeta(persona),
                palette = persona.model.palette,
                favorite = null,
                mark = ModelPickerMark.Active.takeIf { active.persona?.id == persona.id },
            )
        }
    } else {
        emptyList()
    }
    return ModelPickerUiState(
        variant = variant,
        favorites = favorites.map { modelRow(section = "favorite", model = it, star = false) },
        personas = personaRows,
        allModels = ModelCatalogue.models.map { modelRow(section = "model", model = it, star = true) },
    )
}

/** A model row's meta: `` `${provider} · ${ctx} ctx · ${cost}` ``, "Anthropic · 200k ctx · $$". */
fun modelRowMeta(model: ChatModel): String =
    listOf(model.provider, "${model.contextWindow} ctx", model.cost).joinToString(MetaSeparator)

/** A persona row's meta: `` `→ ${short} · ${prompt}` ``, "→ sonnet-4.5 · Correctness first, …". */
fun personaRowMeta(persona: Persona): String = "→ ${persona.model.shortName}$MetaSeparator${persona.prompt}"
