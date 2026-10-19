package cl.withyou.app.domain.model

/**
 * Qué debe hacer la app con lo que dijo el usuario.
 * No contiene textos: la capa de UI los arma desde strings.xml.
 */
sealed interface Decision {

    /** Acción no crítica: se ejecuta de inmediato (linterna, volumen, hora, fecha). */
    data class Execute(val intent: UserIntent) : Decision

    /** Acción crítica: "¿Confirmas que quieres…?" (RF-10, CU-08). */
    data class AskConfirmation(val intent: UserIntent) : Decision

    /** "¿Quisiste decir…?" con hasta 3 opciones (RF-04, CU-06). Las opciones pueden venir vacías. */
    data class AskClarification(
        val reason: ClarificationReason,
        val options: List<UserIntent> = emptyList(),
    ) : Decision

    /** Falta un dato para completar la acción ("¿A qué hora?", "¿A quién?", "¿Qué le digo?"). */
    data class AskForSlot(val intent: UserIntent, val slot: Slot) : Decision

    /** El usuario respondió sí o no; el ViewModel decide a qué pregunta pendiente aplica. */
    data class Reply(val affirmative: Boolean) : Decision

    /** Los datos interpretados no son válidos y la acción no se puede ejecutar. */
    data class Reject(val reason: RejectReason, val intent: UserIntent) : Decision
}

enum class ClarificationReason {
    NOT_UNDERSTOOD,
    LOW_CONFIDENCE,
    AMBIGUOUS_PERIOD,
    AMBIGUOUS_CONTACT,
    CONTACT_NOT_FOUND,
}

enum class Slot { TIME, CONTACT, MESSAGE_TEXT }

enum class RejectReason {
    INVALID_TIME,
    TIME_ALREADY_PASSED,
    INVALID_PHONE,
    MESSAGE_TOO_LONG,
}
