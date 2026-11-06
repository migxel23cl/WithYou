package cl.withyou.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.withyou.app.WithYouApp
import cl.withyou.app.data.prefs.PreferencesRepository
import cl.withyou.app.domain.model.AccessibilityPreferences
import cl.withyou.app.services.TtsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Preferencias de accesibilidad (RF-12, mockup 7). Lo usan la pantalla Ajustes y MainActivity,
 * que vuelve a dibujar la app cuando cambia el tamaño del texto.
 */
class SettingsViewModel(
    private val repository: PreferencesRepository,
    private val tts: TtsService,
) : ViewModel() {

    val preferences: StateFlow<AccessibilityPreferences> =
        repository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, AccessibilityPreferences())

    /**
     * Tamaño de texto guardado, solo cuando cambia. A diferencia de [preferences], no parte con el
     * valor por defecto: así MainActivity no confunde "aún no se lee" con "cambió".
     */
    val textScale: Flow<Float> = repository.preferences.map { it.textScale }.distinctUntilChanged()

    fun setTextScale(scale: Float) {
        viewModelScope.launch { repository.setTextScale(scale) }
    }

    fun setVoiceVolume(volume: Float) {
        viewModelScope.launch { repository.setVoiceVolume(volume) }
    }

    fun setVoiceFeedback(enabled: Boolean) {
        viewModelScope.launch { repository.setVoiceFeedback(enabled) }
    }

    /** Dice [sample] con el volumen elegido, para que el usuario escuche cómo quedó. */
    fun previewVoice(sample: String, volume: Float = preferences.value.voiceVolume) {
        tts.speak(sample, AccessibilityPreferences.clampVoiceVolume(volume))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WithYouApp
                SettingsViewModel(app.container.preferencesRepository, app.container.ttsService)
            }
        }
    }
}
