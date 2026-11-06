package cl.withyou.app.ui.result

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cl.withyou.app.R
import cl.withyou.app.databinding.FragmentResultBinding
import cl.withyou.app.ui.assistant.AssistantScreen
import cl.withyou.app.ui.assistant.AssistantViewModel
import cl.withyou.app.ui.assistant.handleBackWith
import kotlinx.coroutines.launch

/** Pantalla "Resultado": check verde, qué se hizo y vuelta automática al Inicio. */
class ResultFragment : Fragment(R.layout.fragment_result) {

    private var binding: FragmentResultBinding? = null
    private val viewModel: AssistantViewModel by activityViewModels { AssistantViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentResultBinding.bind(view).also { binding = it }

        binding.backHomeButton.setOnClickListener { viewModel.goHome() }
        handleBackWith(viewModel)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.screen == AssistantScreen.RESULT) binding.resultMessage.text = state.message
                    }
                }
                launch {
                    viewModel.isSpeaking.collect { speaking ->
                        binding.speakingText.visibility = if (speaking) View.VISIBLE else View.INVISIBLE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
