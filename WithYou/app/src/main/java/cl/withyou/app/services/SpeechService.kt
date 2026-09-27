package cl.withyou.app.services

import kotlinx.coroutines.flow.Flow

/** Convierte la voz del usuario en texto (RF-01, RF-02). */
interface SpeechService {

    val events: Flow<SpeechEvent>

    /** Debe llamarse desde el hilo principal. */
    fun startListening()

    fun stopListening()
}

sealed interface SpeechEvent {

    /** El micrófono está abierto y listo para escuchar. */
    data object Ready : SpeechEvent

    /** El usuario dejó de hablar; los resultados vienen en camino. */
    data object EndOfSpeech : SpeechEvent

    /** Texto provisional mientras el usuario habla. */
    data class Partial(val text: String) : SpeechEvent

    /** Hasta 3 transcripciones posibles, de la más probable a la menos probable. */
    data class Results(val alternatives: List<String>) : SpeechEvent

    data class Error(val reason: SpeechError) : SpeechEvent
}

enum class SpeechError {
    /** Se escuchó algo, pero no se pudo transcribir. */
    NO_MATCH,

    /** No se escuchó nada. */
    NO_SPEECH,
    NETWORK,
    BUSY,
    NO_PERMISSION,

    /** El teléfono no tiene reconocimiento de voz o no soporta el idioma. */
    UNAVAILABLE,
    OTHER,
}
