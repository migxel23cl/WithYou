package cl.withyou.app.domain.model

/**
 * Preferencias de accesibilidad (RF-12, mockup 7). Los valores siempre quedan dentro de rangos
 * seguros: el texto nunca más chico que el normal y la voz nunca en silencio.
 */
data class AccessibilityPreferences(
    /** Multiplica el tamaño del texto de toda la app (1.0 = normal). */
    val textScale: Float = DEFAULT_TEXT_SCALE,
    /** Volumen de la voz de la app, relativo al volumen del teléfono (1.0 = máximo). */
    val voiceVolume: Float = DEFAULT_VOICE_VOLUME,
    /** Si la app responde también por voz (RF-13). La respuesta en pantalla se muestra siempre. */
    val voiceFeedback: Boolean = true,
) {
    companion object {
        const val MIN_TEXT_SCALE = 1.0f
        const val MAX_TEXT_SCALE = 1.6f
        const val DEFAULT_TEXT_SCALE = 1.0f

        const val MIN_VOICE_VOLUME = 0.2f
        const val MAX_VOICE_VOLUME = 1.0f
        const val DEFAULT_VOICE_VOLUME = 1.0f

        fun clampTextScale(value: Float): Float = value.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)

        fun clampVoiceVolume(value: Float): Float = value.coerceIn(MIN_VOICE_VOLUME, MAX_VOICE_VOLUME)
    }
}
