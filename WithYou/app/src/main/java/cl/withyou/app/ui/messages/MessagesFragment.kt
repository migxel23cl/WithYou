package cl.withyou.app.ui.messages

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import cl.withyou.app.R
import cl.withyou.app.databinding.FragmentMessagesBinding
import cl.withyou.app.ui.MicPermissionRequester
import cl.withyou.app.ui.assistant.AssistantScreen
import cl.withyou.app.ui.assistant.AssistantUiState
import cl.withyou.app.ui.assistant.AssistantViewModel
import cl.withyou.app.ui.assistant.VoiceState
import cl.withyou.app.ui.openTab
import kotlinx.coroutines.launch

/**
 * Pantalla Mensajes (CU-03, mockup 3): se elige a quién escribir y se dicta el mensaje.
 * La elección la guarda MessagesViewModel; el dictado y la confirmación, AssistantViewModel.
 */
class MessagesFragment : Fragment(R.layout.fragment_messages) {

    private var binding: FragmentMessagesBinding? = null
    private val viewModel: MessagesViewModel by viewModels { MessagesViewModel.Factory }
    private val assistant: AssistantViewModel by activityViewModels { AssistantViewModel.Factory }

    private val adapter = RecipientAdapter(onSelect = { viewModel.select(it) })

    private val micPermission = MicPermissionRequester(this) { onDictate() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentMessagesBinding.bind(view).also { binding = it }

        binding.recipientsList.layoutManager = LinearLayoutManager(requireContext())
        binding.recipientsList.adapter = adapter
        binding.micButton.setOnClickListener { micPermission.runWithPermission() }
        binding.goToContactsButton.setOnClickListener { openTab(R.id.contactsFragment) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderRecipients) }
                launch { assistant.uiState.collect(::renderVoice) }
            }
        }
    }

    override fun onDestroyView() {
        binding?.recipientsList?.adapter = null
        binding = null
        super.onDestroyView()
    }

    /** Tocar el micrófono mientras escucha termina el dictado; si no, empieza a dictar al elegido. */
    private fun onDictate() {
        if (assistant.uiState.value.voice == VoiceState.LISTENING) {
            assistant.onMicClicked()
            return
        }
        val selected = viewModel.uiState.value.selected
        if (selected == null) {
            binding?.statusText?.setText(R.string.dictate_message_choose_first)
        } else {
            assistant.dictateMessageTo(selected)
        }
    }

    private fun renderRecipients(state: MessagesUiState) {
        val binding = binding ?: return
        adapter.submit(state.contacts, state.selected)
        val empty = state.contacts.isEmpty()
        binding.emptyGroup.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recipientsList.visibility = if (empty) View.GONE else View.VISIBLE
        binding.dictateGroup.visibility = if (empty) View.GONE else View.VISIBLE
    }

    /** Muestra el estado del dictado: escuchando, lo que se va entendiendo o un error. */
    private fun renderVoice(state: AssistantUiState) {
        val binding = binding ?: return
        if (state.screen != AssistantScreen.HOME) return
        val listening = state.voice == VoiceState.LISTENING
        binding.micButton.isActivated = listening
        val micText = getString(if (listening) R.string.dictate_message_stop else R.string.dictate_message)
        binding.micButton.contentDescription = micText
        binding.micLabel.text = micText

        val status = when {
            listening && state.partialText.isNotBlank() -> getString(R.string.home_heard, state.partialText)
            else -> state.message
        }
        binding.statusText.text = status
        binding.statusText.visibility = if (status.isNullOrBlank()) View.GONE else View.VISIBLE
    }
}
