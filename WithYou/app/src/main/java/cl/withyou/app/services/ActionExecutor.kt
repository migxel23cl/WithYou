package cl.withyou.app.services

import cl.withyou.app.domain.model.VolumeDirection

/** Ejecuta acciones del teléfono. Detrás de una interfaz para poder reemplazarlo en las pruebas. */
interface ActionExecutor {

    fun setFlashlight(on: Boolean): ActionResult

    fun changeVolume(direction: VolumeDirection): ActionResult

    /** Crea una alarma en el reloj del teléfono para la próxima vez que llegue esa hora (RF-05). */
    fun setAlarm(hour: Int, minute: Int, label: String): ActionResult

    /**
     * Llama al número (RF-06). Con permiso `CALL_PHONE` llama directo ([ActionResult.SUCCESS]);
     * sin él abre el teléfono con el número marcado ([ActionResult.NEEDS_USER_STEP]).
     */
    fun call(phone: String): ActionResult

    /**
     * Abre la app de mensajes con el texto listo para [phone] (RF-07); el usuario toca "Enviar"
     * ([ActionResult.NEEDS_USER_STEP]). No requiere el permiso SEND_SMS.
     */
    fun sendMessage(phone: String, text: String): ActionResult
}

enum class ActionResult {
    SUCCESS,

    /** El teléfono no tiene esa función (p. ej. no tiene flash). */
    NOT_SUPPORTED,

    /** Ya está en el límite (volumen al máximo o al mínimo). */
    AT_LIMIT,

    /** Se abrió otra app y el usuario debe terminar la acción (p. ej. tocar "Llamar" en el teléfono). */
    NEEDS_USER_STEP,
    FAILED,
}
