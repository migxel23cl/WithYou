package cl.withyou.app.services

import kotlinx.coroutines.flow.StateFlow

/** Lee en voz alta las respuestas de la app (RF-13). */
interface TtsService {

    val isSpeaking: StateFlow<Boolean>

    /** Dice [text], interrumpiendo lo que se estuviera diciendo. */
    fun speak(text: String)

    fun stop()
}
