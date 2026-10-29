package br.com.weslleycampos.chat.data.chat.models

/**
 * A language voice mode listens and speaks in, and asks the model to answer in, as Settings picks
 * it ([VoiceLanguages]). The device's speech recognition and synthesis do best when told the
 * language rather than left on the device's own, and a reply in that language is one its voice can
 * say.
 *
 * @property tag Its IETF BCP 47 tag, as Settings shows it and the speech engines take it.
 * @property displayName Its own name for itself, as screen readers announce it.
 * @property promptName How the voice prompt names it to the model.
 */
enum class VoiceLanguage(val tag: String, val displayName: String, internal val promptName: String) {
    PORTUGUESE_BRAZIL(tag = "pt-BR", displayName = "Português (Brasil)", promptName = "Brazilian Portuguese"),
    ENGLISH_US(tag = "en-US", displayName = "English (US)", promptName = "American English"),
    ;

    companion object {
        /**
         * The language a device set to [tag] ("pt-BR", "pt_PT", "en-GB") starts with: Brazilian
         * Portuguese for any Portuguese, American English for anything else.
         */
        fun forDevice(tag: String): VoiceLanguage =
            if (tag.split('-', '_').first().equals("pt", ignoreCase = true)) PORTUGUESE_BRAZIL else ENGLISH_US

        /** The language whose [VoiceLanguage.tag] is [tag], or `null` for one this build does not have. */
        fun ofTag(tag: String): VoiceLanguage? = entries.firstOrNull { it.tag == tag }

        /**
         * The language a speech engine reports it heard as [tag] ("en-US", "en", "pt_BR"), by its
         * language alone, or `null` for one this build does not have.
         */
        fun heardAs(tag: String): VoiceLanguage? =
            entries.firstOrNull { it.tag.substringBefore('-').equals(tag.split('-', '_').first(), ignoreCase = true) }
    }
}
