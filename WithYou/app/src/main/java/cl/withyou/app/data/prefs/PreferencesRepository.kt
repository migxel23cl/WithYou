package cl.withyou.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cl.withyou.app.domain.model.AccessibilityPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preferencias de accesibilidad (Repository de MVVM). Los ViewModels dependen de esta
 * interfaz y no de DataStore, así se pueden probar con un repositorio en memoria.
 */
interface PreferencesRepository {

    /** Siempre actualizadas; al inicio, los valores por defecto. */
    val preferences: Flow<AccessibilityPreferences>

    suspend fun setTextScale(scale: Float)

    suspend fun setVoiceVolume(volume: Float)

    suspend fun setVoiceFeedback(enabled: Boolean)
}

/** Implementación con Jetpack DataStore (decisión 2 del diseño: DataStore solo para preferencias). */
class DataStorePreferencesRepository(private val dataStore: DataStore<Preferences>) : PreferencesRepository {

    override val preferences: Flow<AccessibilityPreferences> = dataStore.data.map { stored ->
        AccessibilityPreferences(
            textScale = AccessibilityPreferences.clampTextScale(
                stored[TEXT_SCALE] ?: AccessibilityPreferences.DEFAULT_TEXT_SCALE,
            ),
            voiceVolume = AccessibilityPreferences.clampVoiceVolume(
                stored[VOICE_VOLUME] ?: AccessibilityPreferences.DEFAULT_VOICE_VOLUME,
            ),
            voiceFeedback = stored[VOICE_FEEDBACK] ?: true,
        )
    }

    override suspend fun setTextScale(scale: Float) {
        dataStore.edit { it[TEXT_SCALE] = AccessibilityPreferences.clampTextScale(scale) }
    }

    override suspend fun setVoiceVolume(volume: Float) {
        dataStore.edit { it[VOICE_VOLUME] = AccessibilityPreferences.clampVoiceVolume(volume) }
    }

    override suspend fun setVoiceFeedback(enabled: Boolean) {
        dataStore.edit { it[VOICE_FEEDBACK] = enabled }
    }

    companion object {
        private val TEXT_SCALE = floatPreferencesKey("text_scale")
        private val VOICE_VOLUME = floatPreferencesKey("voice_volume")
        private val VOICE_FEEDBACK = booleanPreferencesKey("voice_feedback")
    }
}

/** Un solo DataStore por archivo en toda la app (requisito de DataStore). */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
