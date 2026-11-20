package cl.withyou.app.ui.confirm

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cl.withyou.app.R
import cl.withyou.app.databinding.FragmentConfirmBinding
import cl.withyou.app.ui.assistant.AssistantScreen
import cl.withyou.app.ui.assistant.AssistantUiState
import cl.withyou.app.ui.assistant.AssistantViewModel
import cl.withyou.app.ui.assistant.SecondaryAction
import cl.withyou.app.ui.assistant.handleBackWith
import cl.withyou.app.ui.assistant.renderAnswerMic
import cl.withyou.app.ui.assistant.renderHeard
import kotlinx.coroutines.launch

/** Pantalla "Confirmar acción" (CU-08, RF-10): "Sí, …" en verde y "Cancelar" en rojo, o "sí"/"no" por voz. */
class ConfirmFragment : Fragment(R.layout.fragment_confirm) {

    private var binding: FragmentConfirmBinding? = null
    private val viewModel: AssistantViewModel by activityViewModels { AssistantViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentConfirmBinding.bind(view).also { binding = it }

        binding.confirmButton.setOnClickListener { viewModel.onConfirm() }
        binding.cancelButton.setOnClickListener { viewModel.onCancel() }
        binding.redictateButton.setOnClickListener { viewModel.onSecondaryAction() }
        binding.micButton.setOnClickListener { viewModel.onMicClicked() }
        handleBackWith(viewModel)

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
        if (state.screen != AssistantScreen.CONFIRM) return
        state.confirmation?.let {
            binding.confirmTitle.text = it.title
            binding.confirmDetail.text = it.detail
            binding.confirmDetail.visibility = if (it.detail.isBlank()) View.GONE else View.VISIBLE
            binding.confirmButton.text = it.confirmLabel
        }
        val secondaryText = when (state.secondaryAction) {
            SecondaryAction.REDICTATE_MESSAGE -> R.string.redictate_message
            SecondaryAction.NAME_ALARM -> R.string.name_alarm
            SecondaryAction.RENAME_ALARM -> R.string.rename_alarm
            SecondaryAction.NONE -> null
        }
        binding.redictateButton.visibility = if (secondaryText != null) View.VISIBLE else View.GONE
        if (secondaryText != null) binding.redictateButton.setText(secondaryText)
        binding.assistantMessage.text = state.message
        renderHeard(binding.heardText, state)
        renderAnswerMic(binding.micButton, binding.micLabel, state)
    }
}
