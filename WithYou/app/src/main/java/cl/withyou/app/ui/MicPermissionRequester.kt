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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.mic_permission_title)
            .setMessage(R.string.mic_permission_message)
            .setPositiveButton(R.string.button_continue) { _, _ -> launcher.launch(Manifest.permission.RECORD_AUDIO) }
            .setNegativeButton(R.string.button_not_now, null)
            .show()
    }

    private fun showOpenSettingsDialog() {
        val context = fragment.requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.mic_permission_settings_title)
            .setMessage(R.string.mic_permission_settings_message)
            .setPositiveButton(R.string.button_open_settings) { _, _ ->
                val uri = Uri.fromParts("package", context.packageName, null)
                fragment.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            }
            .setNegativeButton(R.string.button_not_now, null)
            .show()
    }
}
