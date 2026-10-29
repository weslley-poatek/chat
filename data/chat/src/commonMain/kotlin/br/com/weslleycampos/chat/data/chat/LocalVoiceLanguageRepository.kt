package br.com.weslleycampos.chat.data.chat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [VoiceLanguageRepository] on the app's plain DataStore: the primary language's tag under
 * [LanguageKey] and whether voice mode is bilingual under [BilingualKey]. While no language is
 * stored, or one a later build dropped, the primary is the language of [deviceLanguage], the
 * device's language tag as it is when read.
 */
internal class LocalVoiceLanguageRepository(
    private val preferences: DataStore<Preferences>,
    private val deviceLanguage: () -> String,
) : VoiceLanguageRepository {
    override fun observeLanguages(): Flow<VoiceLanguages> = preferences.data
        .map { stored ->
            VoiceLanguages(
                primary = stored[LanguageKey]?.let(VoiceLanguage::ofTag) ?: VoiceLanguage.forDevice(deviceLanguage()),
                bilingual = stored[BilingualKey] ?: false,
            )
        }
        .distinctUntilChanged()

    override suspend fun setLanguages(languages: VoiceLanguages) {
        preferences.edit {
            it[LanguageKey] = languages.primary.tag
            it[BilingualKey] = languages.bilingual
        }
    }

    companion object {
        /** The primary language's tag. Absent until languages are first picked. */
        val LanguageKey: Preferences.Key<String> = stringPreferencesKey("voice_language")

        /** Whether voice mode listens for every language. Absent until languages are first picked. */
        val BilingualKey: Preferences.Key<Boolean> = booleanPreferencesKey("voice_bilingual")
    }
}
