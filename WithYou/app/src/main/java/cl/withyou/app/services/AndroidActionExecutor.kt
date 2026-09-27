package cl.withyou.app.services

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.util.Log
import cl.withyou.app.domain.model.VolumeDirection

class AndroidActionExecutor(context: Context) : ActionExecutor {

    private val cameraManager = context.applicationContext.getSystemService(CameraManager::class.java)
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)

    /** La linterna no requiere el permiso de cámara. */
    override fun setFlashlight(on: Boolean): ActionResult {
        val cameraId = torchCameraId() ?: return ActionResult.NOT_SUPPORTED
        return try {
            cameraManager.setTorchMode(cameraId, on)
            ActionResult.SUCCESS
        } catch (e: CameraAccessException) {
            Log.w(TAG, "No se pudo cambiar la linterna", e)
            ActionResult.FAILED
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "No se pudo cambiar la linterna", e)
            ActionResult.FAILED
        }
    }

    /**
     * Cambia el volumen multimedia, que es el mismo que usa la voz de la app.
     * No lo baja a 0, para que el usuario siga escuchando las respuestas.
     */
    override fun changeVolume(direction: VolumeDirection): ActionResult {
        val current = audioManager.getStreamVolume(STREAM)
        val max = audioManager.getStreamMaxVolume(STREAM)
        val atLimit = when (direction) {
            VolumeDirection.UP -> current >= max
            VolumeDirection.DOWN -> current <= MIN_AUDIBLE_VOLUME
        }
        if (atLimit) return ActionResult.AT_LIMIT

        val adjustment = when (direction) {
            VolumeDirection.UP -> AudioManager.ADJUST_RAISE
            VolumeDirection.DOWN -> AudioManager.ADJUST_LOWER
        }
        return try {
            audioManager.adjustStreamVolume(STREAM, adjustment, AudioManager.FLAG_SHOW_UI)
            ActionResult.SUCCESS
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo cambiar el volumen", e)
            ActionResult.FAILED
        }
    }

    private fun torchCameraId(): String? = try {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: CameraAccessException) {
        Log.w(TAG, "No se pudo consultar la cámara", e)
        null
    }

    private companion object {
        const val TAG = "WithYou.Actions"
        const val STREAM = AudioManager.STREAM_MUSIC
        const val MIN_AUDIBLE_VOLUME = 1
    }
}
