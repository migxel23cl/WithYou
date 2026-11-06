package cl.withyou.app.ui.assistant

/**
 * Estado de la conversación con el asistente. Lo comparten Inicio, Confirmar,
 * Aclarar y Resultado: cada pantalla dibuja solo la parte que le corresponde.
 */
data class AssistantUiState(
    /** Pantalla que debe estar visible; MainActivity navega cuando cambia. */
    val screen: AssistantScreen = AssistantScreen.HOME,
    val voice: VoiceState = VoiceState.IDLE,
    /** Texto provisional mientras el usuario habla. */
    val partialText: String = "",
    /** Lo que la app entendió que dijo el usuario. */
    val heard: String? = null,
    /** Respuesta o pregunta de la app: se muestra y se dice en voz alta a la vez (RF-13). */
    val message: String? = null,
    val kind: ResponseKind = ResponseKind.INFO,
    /** Datos de la acción por confirmar (pantalla Confirmar). */
    val confirmation: ConfirmationTexts? = null,
    /** Opciones de "¿Quisiste decir…?" (pantalla Aclarar), como máximo 3. */
    val options: List<String> = emptyList(),
    /** En Confirmar, muestra "Corregir mensaje" (solo para mensajes, CU-03). */
    val canRedictate: Boolean = false,
)

enum class AssistantScreen { HOME, CONFIRM, CLARIFY, RESULT }

enum class VoiceState { IDLE, LISTENING, PROCESSING }

enum class ResponseKind { SUCCESS, INFO, ERROR }

/** Textos de la pantalla Confirmar, según el mockup ("¿Llamar a María?", "Sí, llamar"). */
data class ConfirmationTexts(
    val title: String,
    val detail: String,
    val question: String,
    val confirmLabel: String,
)
