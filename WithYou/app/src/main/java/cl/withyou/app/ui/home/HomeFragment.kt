package cl.withyou.app.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cl.withyou.app.R
import cl.withyou.app.databinding.FragmentHomeBinding
import cl.withyou.app.ui.MicPermissionRequester
import cl.withyou.app.ui.assistant.AssistantScreen
import cl.withyou.app.ui.assistant.AssistantUiState
import cl.withyou.app.ui.assistant.AssistantViewModel
import cl.withyou.app.ui.assistant.ResponseKind
import cl.withyou.app.ui.assistant.VoiceState
import cl.withyou.app.ui.openTab
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var binding: FragmentHomeBinding? = null

    /** Compartido con Confirmar, Aclarar, Resultado y Mensajes: una conversación pasa por varias pantallas. */
    private val viewModel: AssistantViewModel by activityViewModels { AssistantViewModel.Factory }

    private val micPermission = MicPermissionRequester(this) { viewModel.onMicClicked() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomeBinding.bind(view).also { binding = it }

        binding.dateText.text = viewModel.todayHeader()
        binding.micButton.setOnClickListener { micPermission.runWithPermission() }
        binding.actionAlarm.setOnClickListener { viewModel.onAlarmQuickAction() }
        binding.actionFlashlight.setOnClickListener { viewModel.onFlashlightQuickAction() }
        binding.actionCall.setOnClickListener { openTab(R.id.contactsFragment) }
        binding.actionMessage.setOnClickListener { openTab(R.id.messagesFragment) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(state: AssistantUiState) {
        val binding = binding ?: return
        // Mientras MainActivity cambia a otra pantalla del asistente, Inicio no se redibuja.
        if (state.screen != AssistantScreen.HOME) return
        val listening = state.voice == VoiceState.LISTENING

        binding.micButton.isActivated = listening
        binding.micButton.contentDescription = getString(
            if (listening) R.string.home_mic_stop_description else R.string.home_mic_description,
        )
        binding.statusText.setText(
            when (state.voice) {
                VoiceState.LISTENING -> R.string.home_listening
                VoiceState.PROCESSING -> R.string.home_thinking
                VoiceState.IDLE -> R.string.home_tap_to_talk
            },
        )

        val heard = if (listening) state.partialText.takeIf { it.isNotBlank() } else state.heard
        binding.heardCard.visibility = if (heard != null) View.VISIBLE else View.GONE
        if (heard != null) binding.heardText.text = getString(R.string.home_heard, heard)

        // Una pregunta pendiente ("¿Qué le quieres decir?") sigue visible mientras se escucha la respuesta.
        val message = state.message
        if (message != null) {
            val color = ContextCompat.getColor(
                requireContext(),
                when (state.kind) {
                    ResponseKind.SUCCESS -> R.color.confirm
                    ResponseKind.INFO -> R.color.primary
                    ResponseKind.ERROR -> R.color.cancel
                },
            )
            binding.responseText.text = message
            binding.responseText.setTextColor(color)
            binding.responseCard.strokeColor = color
            binding.responseCard.visibility = View.VISIBLE
        } else {
            binding.responseCard.visibility = View.GONE
        }
    }
}
