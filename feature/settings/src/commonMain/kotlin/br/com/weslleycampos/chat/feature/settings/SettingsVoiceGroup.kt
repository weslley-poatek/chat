package br.com.weslleycampos.chat.feature.settings

import br.com.weslleycampos.chat.data.chat.VoiceLanguageRepository
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import br.com.weslleycampos.chat.feature.settings.resources.FeatureSettingsRes
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_language_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * The "voice" group's logic: [SettingsUiState.languages] follows [languages], those voice mode
 * listens, asks for its replies and speaks in, and a tap picks others. A pick that cannot be stored
 * says so in the [toast], and the group keeps showing the languages voice mode is in.
 */
class SettingsVoiceGroup internal constructor(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SettingsUiState>,
    private val languages: VoiceLanguageRepository,
    private val toast: TimedValue<StringResource>,
) {
    init {
        scope.launch {
            languages.observeLanguages()
                // The store could not be read: the group stays hidden rather than showing a pick.
                .catch { }
                .collect { picked -> state.update { it.copy(languages = picked) } }
        }
    }

    /**
     * A tap on a segment, [picked] being what it picks ([options]): voice mode is in those languages
     * from its next listen. Ignored while the account is being deleted.
     */
    fun onLanguagesClick(picked: VoiceLanguages) {
        if (state.value.deleting) return
        scope.launch {
            toast.showOnFailure(FeatureSettingsRes.string.settings_toast_language_failed) {
                languages.setLanguages(picked)
                true
            }
        }
    }
}
