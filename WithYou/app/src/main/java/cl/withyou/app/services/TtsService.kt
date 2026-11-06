package cl.withyou.app.services

import kotlinx.coroutines.flow.StateFlow

/** Lee en voz alta las respuestas de la app (RF-13). */
interface TtsService {

    val isSpeaking: StateFlow<Boolean>

    /**
     * Dice [text], interrumpiendo lo que se estuviera diciendo. [volume] va de 0 a 1 y es relativo
     * al volumen del teléfono (preferencia "volumen de voz", RF-12).
     */
    fun speak(text: String, volume: Float = 1f)

    fun stop()
}
