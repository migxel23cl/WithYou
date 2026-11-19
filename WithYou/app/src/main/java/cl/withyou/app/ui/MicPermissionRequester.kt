package cl.withyou.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import cl.withyou.app.R
import com.google.android.material.snackbar.Snackbar

/**
 * Pide el permiso del micrófono explicando antes para qué se usa (RNF-06). Lo usan Inicio y
 * Mensajes. Es parte de la View: pedir permisos requiere un Fragment o una Activity.
 *
 * Debe crearse como propiedad del Fragment (antes de onCreate) para registrar el resultado.
 */
class MicPermissionRequester(private val fragment: Fragment, private val onGranted: () -> Unit) {

    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> onGranted()
            // Si Android ya no muestra el diálogo, el permiso quedó denegado de forma permanente.
            !fragment.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> showOpenSettingsDialog()
            else -> fragment.view?.let { Snackbar.make(it, R.string.mic_permission_denied, Snackbar.LENGTH_LONG).show() }
        }
    }

    /** Ejecuta [onGranted] si ya hay permiso; si no, explica y lo pide. */
    fun runWithPermission() {
        val context = fragment.requireContext()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onGranted() else showRationale()
    }

    private fun showRationale() {
        val context = fragment.requireContext()
        context.showAppDialog(
            title = context.getString(R.string.mic_permission_title),
            message = context.getString(R.string.mic_permission_message),
            positiveText = context.getString(R.string.button_continue),
            negativeText = context.getString(R.string.button_not_now),
        ) { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }

    private fun showOpenSettingsDialog() {
        val context = fragment.requireContext()
        context.showAppDialog(
            title = context.getString(R.string.mic_permission_settings_title),
            message = context.getString(R.string.mic_permission_settings_message),
            positiveText = context.getString(R.string.button_open_settings),
            negativeText = context.getString(R.string.button_not_now),
        ) {
            val uri = Uri.fromParts("package", context.packageName, null)
            fragment.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
        }
    }
}
