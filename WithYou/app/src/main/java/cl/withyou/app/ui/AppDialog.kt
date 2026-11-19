package cl.withyou.app.ui

import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import cl.withyou.app.R
import cl.withyou.app.databinding.DialogAppBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Diálogo responsive de toda la app (capa View). A diferencia del diálogo estándar de Android,
 * el título, el mensaje y el contenido se desplazan y **los botones quedan siempre visibles abajo**:
 * en pantallas pequeñas, con el texto agrandado (RF-12) o con el teclado abierto.
 *
 * @param content campos propios (formularios); null si solo hay mensaje.
 * @param closeOnPositive false en formularios: el que llama cierra el diálogo si los datos son válidos.
 */
fun Context.showAppDialog(
    title: CharSequence,
    message: CharSequence? = null,
    content: View? = null,
    positiveText: CharSequence,
    negativeText: CharSequence,
    closeOnPositive: Boolean = true,
    destructiveText: CharSequence? = null,
    onDestructive: (AlertDialog) -> Unit = {},
    onPositive: (AlertDialog) -> Unit,
): AlertDialog {
    val binding = DialogAppBinding.inflate(LayoutInflater.from(this))
    binding.dialogTitle.text = title
    if (message != null) {
        binding.dialogMessage.text = message
        binding.dialogMessage.visibility = View.VISIBLE
    }
    if (content != null) {
        binding.dialogContent.addView(content)
        binding.dialogContent.visibility = View.VISIBLE
    }
    binding.dialogPositiveButton.text = positiveText
    binding.dialogNegativeButton.text = negativeText

    val dialog = MaterialAlertDialogBuilder(this).setView(binding.root).create()
    // Con el teclado abierto, el diálogo se achica (y se desplaza) en vez de quedar tapado.
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    binding.dialogNegativeButton.setOnClickListener { dialog.dismiss() }
    binding.dialogPositiveButton.setOnClickListener {
        onPositive(dialog)
        if (closeOnPositive) dialog.dismiss()
    }
    if (destructiveText != null) {
        binding.dialogDestructiveButton.text = destructiveText
        binding.dialogDestructiveButton.visibility = View.VISIBLE
        binding.dialogDestructiveButton.setOnClickListener {
            dialog.dismiss()
            onDestructive(dialog)
        }
    }
    dialog.show()
    return dialog
}

/** Desplaza el diálogo hasta [view] (p. ej. un campo con su mensaje de error), para que se vea. */
fun AlertDialog.scrollTo(view: View) {
    val scroll = findViewById<ScrollView>(R.id.dialog_scroll) ?: return
    scroll.post {
        val rect = Rect().also(view::getDrawingRect)
        scroll.offsetDescendantRectToMyCoords(view, rect)
        scroll.smoothScrollTo(0, (rect.bottom - scroll.height + scroll.paddingBottom).coerceAtLeast(0))
    }
}
