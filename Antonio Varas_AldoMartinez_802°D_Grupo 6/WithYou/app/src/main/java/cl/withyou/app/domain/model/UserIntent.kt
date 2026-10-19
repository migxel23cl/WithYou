package cl.withyou.app.domain.model

/**
 * Intención del usuario reconocida a partir de su frase.
 *
 * Se llama UserIntent (y no Intent) para no confundirla con android.content.Intent.
 */
sealed interface UserIntent {

    data class SetAlarm(
        val time: TimeSpec,
        val day: AlarmDay = AlarmDay.UNSPECIFIED,
    ) : UserIntent

    data class Call(val target: ContactTarget) : UserIntent

    /** [text] es null cuando el usuario aún no dictó el contenido del mensaje. */
    data class SendMessage(
        val target: ContactTarget,
        val text: String? = null,
    ) : UserIntent

    data class Flashlight(val turnOn: Boolean) : UserIntent

    data class Volume(val direction: VolumeDirection) : UserIntent

    data object AskTime : UserIntent

    data object AskDate : UserIntent

    data object Confirm : UserIntent

    data object Deny : UserIntent

    data object Unknown : UserIntent
}

enum class VolumeDirection { UP, DOWN }

/** Acciones que requieren confirmación antes de ejecutarse (RF-10). */
val UserIntent.isCritical: Boolean
    get() = this is UserIntent.SetAlarm || this is UserIntent.Call || this is UserIntent.SendMessage
