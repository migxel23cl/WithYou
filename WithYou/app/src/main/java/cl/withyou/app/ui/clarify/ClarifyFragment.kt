package cl.withyou.app.ui.clarify

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cl.withyou.app.R
import cl.withyou.app.databinding.FragmentClarifyBinding
import cl.withyou.app.ui.assistant.AssistantScreen
import cl.withyou.app.ui.assistant.AssistantUiState
import cl.withyou.app.ui.assistant.AssistantViewModel
import cl.withyou.app.ui.assistant.handleBackWith
import cl.withyou.app.ui.assistant.renderAnswerMic
import cl.withyou.app.ui.assistant.renderHeard
import kotlinx.coroutines.launch

/** Pantalla "No entendí bien" (CU-06, RF-04): hasta 3 opciones numeradas, "Intentar de nuevo" y "Cancelar". */
class ClarifyFragment : Fragment(R.layout.fragment_clarify) {

    private var binding: FragmentClarifyBinding? = null
    private val viewModel: AssistantViewModel by activityViewModels { AssistantViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentClarifyBinding.bind(view).also { binding = it }

        optionButtons().forEachIndexed { index, button ->
            button.setOnClickListener { viewModel.onOptionSelected(index) }
        }
        binding.tryAgainButton.setOnClickListener { viewModel.onTryAgain() }
        binding.cancelButton.setOnClickListener { viewModel.onCancel() }
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

    private fun optionButtons() = binding?.let { listOf(it.option1, it.option2, it.option3) }.orEmpty()

    private fun render(state: AssistantUiState) {
        val binding = binding ?: return
        if (state.screen != AssistantScreen.CLARIFY) return
        binding.clarifyMessage.text = state.message
        optionButtons().forEachIndexed { index, button ->
            val label = state.options.getOrNull(index)
            button.visibility = if (label != null) View.VISIBLE else View.GONE
            if (label != null) {
                button.text = getString(R.string.clarify_option_label, index + 1, label)
                button.contentDescription = getString(R.string.clarify_option_description, index + 1, label)
            }
        }
        renderHeard(binding.heardText, state)
        renderAnswerMic(binding.micButton, binding.micLabel, state)
    }
}
