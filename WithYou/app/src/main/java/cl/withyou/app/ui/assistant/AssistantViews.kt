package cl.withyou.app.ui.assistant

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import cl.withyou.app.R

/** Partes de la interfaz que se repiten en Confirmar y Aclarar. */

/** Micrófono pequeño para responder por voz: verde mientras escucha. */
internal fun renderAnswerMic(button: ImageButton, label: TextView, state: AssistantUiState) {
    val listening = state.voice == VoiceState.LISTENING
    val text = button.context.getString(if (listening) R.string.answer_by_voice_stop else R.string.answer_by_voice)
    button.isActivated = listening
    button.contentDescription = text
    label.text = text
}

/** "Dijiste: «…»" con lo que se va reconociendo o lo último que se entendió. */
internal fun renderHeard(view: TextView, state: AssistantUiState) {
    val heard = if (state.voice == VoiceState.LISTENING) state.partialText.takeIf { it.isNotBlank() } else state.heard
    view.visibility = if (heard != null) View.VISIBLE else View.GONE
    if (heard != null) view.text = view.context.getString(R.string.home_heard, heard)
}

/** El botón volver del sistema avisa al ViewModel en vez de solo cerrar la pantalla. */
internal fun Fragment.handleBackWith(viewModel: AssistantViewModel) {
    requireActivity().onBackPressedDispatcher.addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = viewModel.onBack()
        },
    )
}
