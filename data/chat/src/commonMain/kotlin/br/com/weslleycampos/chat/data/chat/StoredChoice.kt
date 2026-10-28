package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.data.chat.models.ModelCatalogue
import br.com.weslleycampos.chat.data.chat.models.ModelChoice
import kotlinx.coroutines.flow.first

/** A [ModelChoice] as the `modelId`, `personaId`, `compareOn` and `compareModelId` columns store it. */
internal class StoredChoice(
    val modelId: String,
    val personaId: String? = null,
    val compareOn: Boolean = false,
    val compareModelId: String? = null,
)

/**
 * [choice] as the columns store it: a persona with the model its stored row names and compare off,
 * a model on its own or compared with a second one, or the default model with no persona and compare
 * off for `null`. `null` for a model the catalogue does not have or a persona that is not stored.
 */
internal suspend fun ModelRepository.stored(choice: ModelChoice?): StoredChoice? {
    if (choice == null) return StoredChoice(modelId = observeDefaultModel().first().id)
    val persona = choice.persona ?: return choice.storedModel()
    val stored = observePersonas().first().firstOrNull { it.id == persona.id } ?: return null
    return StoredChoice(modelId = stored.model.id, personaId = stored.id)
}

/**
 * A choice with no persona as the columns store it: its model, and with compare on the second
 * model, which turning compare off drops. `null` for a model the catalogue does not have, or a
 * second model that is not in it or is the first one.
 */
private fun ModelChoice.storedModel(): StoredChoice? {
    val first = ModelCatalogue.find(model.id) ?: return null
    if (!compareOn) return StoredChoice(modelId = first.id)
    val second = compareWith ?: return StoredChoice(modelId = first.id, compareOn = true)
    if (second.id == first.id || ModelCatalogue.find(second.id) == null) return null
    return StoredChoice(modelId = first.id, compareOn = true, compareModelId = second.id)
}
