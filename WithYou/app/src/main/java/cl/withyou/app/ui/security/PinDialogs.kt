package cl.withyou.app.ui.security

import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import cl.withyou.app.R
import cl.withyou.app.databinding.DialogPinBinding
import cl.withyou.app.ui.scrollTo
import cl.withyou.app.ui.showAppDialog

/**
 * Diálogos del PIN (capa View), con AppDialog: los botones siempre visibles. Solo muestran y
 * avisan: validar y guardar lo hace SecurityViewModel.
 */

/** Ejecuta [action] si se puede editar; si hay PIN y está bloqueado, lo pide antes. */
fun Fragment.withPin(security: SecurityViewModel, action: () -> Unit) {
    when (security.uiState.value) {
        SecurityUiState.NO_PIN, SecurityUiState.UNLOCKED -> action()
        SecurityUiState.LOCKED -> askPin(security, action)
        // Se está leyendo el PIN (unos milisegundos): se ignora el toque.
        SecurityUiState.LOADING -> Unit
    }
}

/** Pide el PIN; si es correcto ejecuta [onUnlocked]. Un PIN incorrecto no cierra el diálogo. */
fun Fragment.askPin(security: SecurityViewModel, onUnlocked: () -> Unit = {}) {
    val form = DialogPinBinding.inflate(LayoutInflater.from(requireContext()))
    requireContext().showAppDialog(
        title = getString(R.string.pin_title),
        message = getString(R.string.pin_message),
        content = form.root,
        positiveText = getString(R.string.pin_unlock),
        negativeText = getString(R.string.button_cancel),
        closeOnPositive = false,
    ) { dialog ->
        if (security.unlock(form.pinInput.text.toString())) {
            dialog.dismiss()
            onUnlocked()
        } else {
            form.pinLayout.error = getString(R.string.pin_wrong)
            form.pinInput.text = null
            dialog.scrollTo(form.pinLayout)
        }
    }
    form.pinInput.requestFocus()
}

/** Crea o cambia el PIN, escribiéndolo dos veces para evitar errores. */
fun Fragment.showPinSetup(security: SecurityViewModel, onSaved: () -> Unit) {
    val form = DialogPinBinding.inflate(LayoutInflater.from(requireContext()))
    form.confirmLayout.visibility = View.VISIBLE
    requireContext().showAppDialog(
        title = getString(R.string.pin_setup_title),
        message = getString(R.string.pin_setup_message),
        content = form.root,
        positiveText = getString(R.string.button_save),
        negativeText = getString(R.string.button_cancel),
        closeOnPositive = false,
    ) { dialog ->
        val result = security.setPin(form.pinInput.text.toString(), form.confirmInput.text.toString())
        form.pinLayout.error = if (result == PinChangeResult.INVALID) getString(R.string.pin_invalid) else null
        form.confirmLayout.error = if (result == PinChangeResult.MISMATCH) getString(R.string.pin_mismatch) else null
        when (result) {
            PinChangeResult.SAVED -> {
                dialog.dismiss()
                onSaved()
            }
            PinChangeResult.LOCKED -> dialog.dismiss()
            PinChangeResult.INVALID -> dialog.scrollTo(form.pinLayout)
            PinChangeResult.MISMATCH -> dialog.scrollTo(form.confirmLayout)
        }
    }
    form.pinInput.requestFocus()
}
