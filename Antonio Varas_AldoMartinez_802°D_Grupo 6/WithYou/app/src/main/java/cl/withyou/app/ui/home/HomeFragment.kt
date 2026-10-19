package cl.withyou.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import cl.withyou.app.R
import cl.withyou.app.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var binding: FragmentHomeBinding? = null
    private val viewModel: HomeViewModel by viewModels { HomeViewModel.Factory }

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when {
                granted -> viewModel.onMicClicked()
                // Si Android ya no muestra el diálogo, el permiso quedó denegado de forma permanente.
                !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> showOpenSettingsDialog()
                else -> showMessage(R.string.mic_permission_denied)
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentHomeBinding.bind(view).also { binding = it }

        binding.dateText.text = formatToday()
        binding.micButton.setOnClickListener { onMicTapped() }
        binding.actionAlarm.setOnClickListener { showMessage(R.string.coming_soon) }
        binding.actionFlashlight.setOnClickListener { showMessage(R.string.coming_soon) }
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

    private fun render(state: HomeUiState) {
        val binding = binding ?: return
        val listening = state is HomeUiState.Listening

        binding.micButton.isActivated = listening
        binding.micButton.contentDescription = getString(
            if (listening) R.string.home_mic_stop_description else R.string.home_mic_description,
        )
        binding.statusText.setText(
            when (state) {
                is HomeUiState.Listening -> R.string.home_listening
                is HomeUiState.Processing -> R.string.home_thinking
                else -> R.string.home_tap_to_talk
            },
        )

        val heard = when (state) {
            is HomeUiState.Listening -> state.partialText.takeIf { it.isNotBlank() }
            is HomeUiState.Processing -> state.heard
            is HomeUiState.Responded -> state.heard
            HomeUiState.Idle -> null
        }
        binding.heardCard.visibility = if (heard != null) View.VISIBLE else View.GONE
        if (heard != null) binding.heardText.text = getString(R.string.home_heard, heard)

        if (state is HomeUiState.Responded) {
            val color = ContextCompat.getColor(
                requireContext(),
                when (state.kind) {
                    ResponseKind.SUCCESS -> R.color.confirm
                    ResponseKind.INFO -> R.color.primary
                    ResponseKind.ERROR -> R.color.cancel
                },
            )
            binding.responseText.text = state.message
            binding.responseText.setTextColor(color)
            binding.responseCard.strokeColor = color
            binding.responseCard.visibility = View.VISIBLE
        } else {
            binding.responseCard.visibility = View.GONE
        }
    }

    private fun onMicTapped() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onMicClicked() else showPermissionRationale()
    }

    /** Explica en lenguaje simple para qué se usa el micrófono antes de pedir el permiso (RNF-06). */
    private fun showPermissionRationale() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mic_permission_title)
            .setMessage(R.string.mic_permission_message)
            .setPositiveButton(R.string.button_continue) { _, _ ->
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton(R.string.button_not_now, null)
            .show()
    }

    private fun showOpenSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mic_permission_settings_title)
            .setMessage(R.string.mic_permission_settings_message)
            .setPositiveButton(R.string.button_open_settings) { _, _ ->
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            }
            .setNegativeButton(R.string.button_not_now, null)
            .show()
    }

    private fun openTab(destinationId: Int) {
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.homeFragment, inclusive = false, saveState = true)
            .setRestoreState(true)
            .build()
        findNavController().navigate(destinationId, null, options)
    }

    private fun showMessage(messageId: Int) {
        val root = binding?.root ?: return
        Snackbar.make(root, messageId, Snackbar.LENGTH_LONG).show()
    }

    private fun formatToday(): String {
        val locale = Locale.forLanguageTag("es-CL")
        val text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale))
        return text.replaceFirstChar { it.titlecase(locale) }
    }
}
