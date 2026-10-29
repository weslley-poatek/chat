package br.com.weslleycampos.chat.data.chat.models

/**
 * The languages voice mode works in, as Settings picks them: [primary] alone, or, while [bilingual],
 * every [VoiceLanguage], for someone who switches between Portuguese and English as they talk.
 * Each thing they say is then heard in the language it is in, as far as the device can tell, and
 * answered in it.
 *
 * @property primary The one language, or while bilingual the one listening expects first.
 */
data class VoiceLanguages(val primary: VoiceLanguage, val bilingual: Boolean = false) {
    /** The languages to listen for, [primary] first. */
    val all: List<VoiceLanguage>
        get() = if (bilingual) listOf(primary) + VoiceLanguage.entries.filter { it != primary } else listOf(primary)
}
