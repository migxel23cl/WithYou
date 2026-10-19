package cl.withyou.app.ui.home

sealed interface HomeUiState {

    data object Idle : HomeUiState

    /** Micrófono abierto. [partialText] es lo que se lleva reconocido. */
    data class Listening(val partialText: String = "") : HomeUiState

    data class Processing(val heard: String) : HomeUiState

    /** La app respondió; [message] se muestra y se dice en voz alta a la vez (RF-13). */
    data class Responded(
        val heard: String?,
        val message: String,
        val kind: ResponseKind,
    ) : HomeUiState
}

enum class ResponseKind { SUCCESS, INFO, ERROR }
