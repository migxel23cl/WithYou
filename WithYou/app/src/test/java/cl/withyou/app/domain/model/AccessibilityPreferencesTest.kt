package cl.withyou.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityPreferencesTest {

    @Test
    fun `el tamano del texto queda entre normal y 160 por ciento`() {
        assertEquals(1.0f, AccessibilityPreferences.clampTextScale(0.8f))
        assertEquals(1.3f, AccessibilityPreferences.clampTextScale(1.3f))
        assertEquals(1.6f, AccessibilityPreferences.clampTextScale(3f))
    }

    @Test
    fun `el volumen de la voz nunca queda en silencio`() {
        assertEquals(0.2f, AccessibilityPreferences.clampVoiceVolume(0f))
        assertEquals(0.7f, AccessibilityPreferences.clampVoiceVolume(0.7f))
        assertEquals(1.0f, AccessibilityPreferences.clampVoiceVolume(1.5f))
    }

    @Test
    fun `por defecto texto normal, voz al maximo y respuesta por voz activada`() {
        assertEquals(AccessibilityPreferences(1.0f, 1.0f, true), AccessibilityPreferences())
    }
}
