package br.com.weslleycampos.chat.feature.chat.picker

import androidx.compose.runtime.Immutable
import br.com.weslleycampos.chat.core.ui.theme.ChatPalette
import br.com.weslleycampos.chat.data.chat.models.ChatModel
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import br.com.weslleycampos.chat.data.chat.models.Persona
import br.com.weslleycampos.chat.feature.chat.MetaSeparator
import br.com.weslleycampos.chat.feature.chat.compareLabel
import br.com.weslleycampos.chat.feature.chat.palette
import br.com.weslleycampos.chat.feature.chat.resources.FeatureChatRes
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_title_chat
import br.com.weslleycampos.chat.feature.chat.resources.model_picker_title_default
import org.jetbrains.compose.resources.StringResource

/** Which sheet the Model picker frame is: the prototype's `sheet`. */
enum class ModelPickerVariant(val title: StringResource) {
    /**
     * `sheet:'chat'`, from the model chip: "Model", what answers the chat, personas and the compare
     * button included.
     */
    Chat(FeatureChatRes.string.model_picker_title_chat),

    /**
     * `sheet:'default'`, from Settings' "Default model" (row 10): "Default model", the model new
     * chats start on. A persona is not a default, so it lists no personas, and nothing compares by
     * default, so it has no compare button, hint or "◐" (frames quirk 12 is not reproduced).
     */
    Default(FeatureChatRes.string.model_picker_title_default),
}

/** A row's right-hand mark, mono 12 in the accent. */
enum class ModelPickerMark(val glyph: String) {
    /** The model or persona that answers, the prototype's "●". */
    Active("●"),

    /** The second model of a compare, which answers beside the "●" one: the prototype's "◐". */
    Compare("◐"),
}

/**
 * One row of the sheet, the prototype's `mRow`.
 *
 * @property key Unique in the sheet: a model is listed under "favorites" and again under its vendor.
 * @property choice What a tap on the row picks: the model alone, or the persona on its model.
 * @property meta "claude-sonnet-4-5-20250929 · 200k ctx · $$", or "→ sonnet-4.5 · Correctness first, …"
 *   for a persona.
 * @property palette The 8 dp dot's colour: the model's vendor's accent, a persona's model's for a persona.
 * @property favorite "★" (`true`) or "☆" (`false`) on a vendor's row; `null` elsewhere, no star.
 * @property mark "●" on what answers, "◐" on the model it is compared with, else `null`.
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
 * One vendor's models in the sheet, under its name.
 *
 * @property label "anthropic", "openai": the vendor's name, written as the sheet's section labels are.
 */
@Immutable
data class ModelPickerVendor(val label: String, val rows: List<ModelPickerRow>)

/**
 * Everything the Model picker sheet lists, in its order: "favorites", "personas" (with "+ new
 * persona") on the chat sheet only, then every model the Fuelix key can use, a section per vendor;
 * and on the chat sheet the compare button and, while compare is on, its hint under the title.
 *
 * @property vendors The models by vendor: Anthropic, OpenAI, Google, Meta, DeepSeek, Mistral, then
 *   any other, each listing its models in the order `ModelRepository.observeModels` gives them.
 * @property compareOn The compare button reads "compare · on", filled with the accent.
 * @property compareHint The line under the title while compare is on: "pick a second model to run
 *   beside sonnet-4.5" or "sonnet-4.5 ⇄ gpt-5 · tap another model to swap the second"; else `null`.
 */
@Immutable
data class ModelPickerUiState(
    val variant: ModelPickerVariant,
    val favorites: List<ModelPickerRow>,
    val personas: List<ModelPickerRow>,
    val vendors: List<ModelPickerVendor>,
    val compareOn: Boolean = false,
    val compareHint: String? = null,
) {
    /** Every vendor's rows, in the sheet's order. */
    val allModels: List<ModelPickerRow> get() = vendors.flatMap(ModelPickerVendor::rows)

    /** The "personas" section and "+ new persona": the chat sheet's only. */
    val showsPersonas: Boolean get() = variant == ModelPickerVariant.Chat

    /** The compare button in the header, the prototype's `sheetForChat`: the chat sheet's only. */
    val showsCompare: Boolean get() = variant == ModelPickerVariant.Chat
}

/**
 * The sheet for [variant], marking [active]: the chat's [ModelChoice] on the chat sheet, or
 * `ModelChoice(defaultModel)` on the default one. [models] are the key's, as
 * `ModelRepository.observeModels` gives them, grouped here by vendor; [favorites] come in their
 * order, as `ModelRepository.observeFavorites` gives them, and [personas] in theirs. The default
 * sheet leaves the personas out.
 *
 * The "●" goes on the persona that answers, or, with none, on every row of the model that does,
 * the prototype's `active` in `mRow`; on the chat sheet "◐" goes on every row of the model it is
 * compared with ([ModelChoice.comparing]), the prototype's `isCmp`.
 */
fun modelPickerUiState(
    variant: ModelPickerVariant,
    models: List<ChatModel>,
    favorites: List<ChatModel>,
    personas: List<Persona>,
    active: ModelChoice,
): ModelPickerUiState {
    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
    val chat = variant == ModelPickerVariant.Chat
    val comparing = active.comparing?.takeIf { chat }
    fun modelRow(section: String, model: ChatModel, star: Boolean) = ModelPickerRow(
        key = "$section:${model.id}",
        choice = ModelChoice(model),
        name = model.name,
        meta = modelRowMeta(model),
        palette = model.palette,
        favorite = if (star) model.id in favoriteIds else null,
        mark = when {
            active.persona == null && active.model.id == model.id -> ModelPickerMark.Active
            comparing?.id == model.id -> ModelPickerMark.Compare
            else -> null
        },
    )
    val personaRows = if (chat) {
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
        vendors = models.distinctBy(ChatModel::id).groupBy(ChatModel::vendor).entries
            .sortedBy { (vendor, _) -> vendor.ordinal }
            .map { (vendor, listed) ->
                ModelPickerVendor(
                    label = vendor.displayName.lowercase(),
                    rows = listed.map { modelRow(section = "model", model = it, star = true) },
                )
            },
        compareOn = chat && active.compareOn,
        compareHint = if (chat) compareHint(active) else null,
    )
}

/**
 * The compare hint, the prototype's `compareHint` (line 930), while compare is on: "pick a second
 * model to run beside sonnet-4.5" until a second model is picked, then "sonnet-4.5 ⇄ gpt-5 · tap
 * another model to swap the second". `null` while compare is off.
 */
fun compareHint(choice: ModelChoice): String? {
    if (!choice.compareOn) return null
    val comparing = choice.comparing ?: return "pick a second model to run beside ${choice.model.shortName}"
    return "${compareLabel(choice.model, comparing)}${MetaSeparator}tap another model to swap the second"
}

/**
 * A model row's meta: its Fuelix id, then its context window and cost where known,
 * "claude-sonnet-4-5-20250929 · 200k ctx · $$". The vendor is the section the row is in.
 */
fun modelRowMeta(model: ChatModel): String =
    listOfNotNull(model.id, model.contextWindow?.let { "$it ctx" }, model.cost).joinToString(MetaSeparator)

/** A persona row's meta: `` `→ ${short} · ${prompt}` ``, "→ sonnet-4.5 · Correctness first, …". */
fun personaRowMeta(persona: Persona): String = "→ ${persona.model.shortName}$MetaSeparator${persona.prompt}"
