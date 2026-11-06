package cl.withyou.app.services

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import android.provider.AlarmClock
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.util.Log
import cl.withyou.app.domain.model.VolumeDirection

class AndroidActionExecutor(context: Context) : ActionExecutor {

    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

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

    /**
     * Usa la app de reloj del sistema (`AlarmClock.ACTION_SET_ALARM`), así la alarma suena
     * aunque Withyou esté cerrada. `EXTRA_SKIP_UI` evita abrir el reloj.
     */
    override fun setAlarm(hour: Int, minute: Int, label: String): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(appContext.packageManager) == null) return ActionResult.NOT_SUPPORTED
        return try {
            appContext.startActivity(intent)
            ActionResult.SUCCESS
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No hay app de reloj para crear la alarma", e)
            ActionResult.NOT_SUPPORTED
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo crear la alarma", e)
            ActionResult.FAILED
        }
    }

    override fun call(phone: String): ActionResult {
        val canCallDirectly = ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (canCallDirectly) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.fromParts("tel", phone, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(appContext.packageManager) == null) return ActionResult.NOT_SUPPORTED
        return try {
            appContext.startActivity(intent)
            if (canCallDirectly) ActionResult.SUCCESS else ActionResult.NEEDS_USER_STEP
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No hay app de teléfono para llamar", e)
            ActionResult.NOT_SUPPORTED
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo iniciar la llamada", e)
            ActionResult.FAILED
        }
    }

    override fun sendMessage(phone: String, text: String): ActionResult {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null))
            .putExtra(SMS_BODY, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(appContext.packageManager) == null) return ActionResult.NOT_SUPPORTED
        return try {
            appContext.startActivity(intent)
            ActionResult.NEEDS_USER_STEP
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No hay app de mensajes", e)
            ActionResult.NOT_SUPPORTED
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo abrir la app de mensajes", e)
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

        /** Extra estándar que las apps de mensajes usan para el texto inicial. */
        const val SMS_BODY = "sms_body"
    }
}
