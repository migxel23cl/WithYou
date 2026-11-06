package cl.withyou.app.data.prefs

import cl.withyou.app.domain.model.AccessibilityPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/** Repositorio de preferencias en memoria para las pruebas: limita los valores igual que DataStore. */
class InMemoryPreferencesRepository(
    initial: AccessibilityPreferences = AccessibilityPreferences(),
) : PreferencesRepository {

    override val preferences = MutableStateFlow(initial)

    override suspend fun setTextScale(scale: Float) {
        preferences.value = preferences.value.copy(textScale = AccessibilityPreferences.clampTextScale(scale))
    }

    override suspend fun setVoiceVolume(volume: Float) {
        preferences.value = preferences.value.copy(voiceVolume = AccessibilityPreferences.clampVoiceVolume(volume))
    }

    override suspend fun setVoiceFeedback(enabled: Boolean) {
        preferences.value = preferences.value.copy(voiceFeedback = enabled)
    }
}
