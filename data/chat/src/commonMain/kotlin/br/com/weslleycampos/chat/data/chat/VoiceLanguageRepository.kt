package br.com.weslleycampos.chat.data.chat

import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import kotlinx.coroutines.flow.Flow

/**
 * The [VoiceLanguages] voice mode listens in, asks for its replies in ([VoiceRepository.answer]) and
 * speaks in, as Settings picks them. Until they are picked it is the device's language alone.
 */
interface VoiceLanguageRepository {
    /**
     * The picked languages, or the device's language alone ([VoiceLanguage.forDevice]) while none
     * were. Emits them as soon as it is collected and again whenever they change, never the same
     * twice in a row.
     */
    fun observeLanguages(): Flow<VoiceLanguages>

    /** Picks [languages] for voice mode's next listen, reply and speech. */
    suspend fun setLanguages(languages: VoiceLanguages)
}
