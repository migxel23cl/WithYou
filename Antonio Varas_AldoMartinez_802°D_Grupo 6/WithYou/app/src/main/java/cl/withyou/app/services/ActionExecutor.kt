package cl.withyou.app.services

import cl.withyou.app.domain.model.VolumeDirection

/** Ejecuta acciones del teléfono. Detrás de una interfaz para poder reemplazarlo en las pruebas. */
interface ActionExecutor {

    fun setFlashlight(on: Boolean): ActionResult

    fun changeVolume(direction: VolumeDirection): ActionResult
}

enum class ActionResult {
    SUCCESS,

    /** El teléfono no tiene esa función (p. ej. no tiene flash). */
    NOT_SUPPORTED,

    /** Ya está en el límite (volumen al máximo o al mínimo). */
    AT_LIMIT,
    FAILED,
}
