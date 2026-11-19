package cl.withyou.app.ui.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cl.withyou.app.R
import cl.withyou.app.databinding.FragmentSettingsBinding
import cl.withyou.app.domain.model.AccessibilityPreferences
import cl.withyou.app.ui.security.SecurityUiState
import cl.withyou.app.ui.security.SecurityViewModel
import cl.withyou.app.ui.security.askPin
import cl.withyou.app.ui.security.showPinSetup
import cl.withyou.app.ui.showAppDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Pantalla Ajustes (RF-12, mockup 7): tamaño del texto con vista previa, volumen de la voz y
 * respuesta por voz. Los cambios se guardan al soltar el control. Si el cuidador configuró un
 * PIN, la pantalla queda protegida hasta ingresarlo (§8).
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var binding: FragmentSettingsBinding? = null

    /** true mientras el usuario arrastra un control: así no se le mueve desde [render]. */
    private var tracking = false

    /** Compartido con MainActivity, que aplica el tamaño del texto a toda la app. */
    private val viewModel: SettingsViewModel by activityViewModels { SettingsViewModel.Factory }
    private val security: SecurityViewModel by activityViewModels { SecurityViewModel.Factory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentSettingsBinding.bind(view).also { binding = it }

        binding.textScaleSlider.valueFrom = AccessibilityPreferences.MIN_TEXT_SCALE
        binding.textScaleSlider.valueTo = AccessibilityPreferences.MAX_TEXT_SCALE
        binding.textScaleSlider.setLabelFormatter(::percent)
        binding.textScaleSlider.addOnChangeListener { _, value, _ -> showTextPreview(value) }
        binding.textScaleSlider.onRelease { viewModel.setTextScale(it) }

        binding.voiceVolumeSlider.valueFrom = AccessibilityPreferences.MIN_VOICE_VOLUME
        binding.voiceVolumeSlider.valueTo = AccessibilityPreferences.MAX_VOICE_VOLUME
        binding.voiceVolumeSlider.setLabelFormatter(::percent)
        binding.voiceVolumeSlider.addOnChangeListener { _, value, _ ->
            binding.voiceVolumeValue.text = percent(value)
        }
        binding.voiceVolumeSlider.onRelease {
            viewModel.setVoiceVolume(it)
            viewModel.previewVoice(getString(R.string.settings_voice_sample), it)
        }

        // Se compara con lo guardado (y no con "isPressed") para que también funcione con TalkBack.
        binding.voiceFeedbackSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != viewModel.preferences.value.voiceFeedback) viewModel.setVoiceFeedback(checked)
        }

        binding.unlockButton.setOnClickListener { askPin(security) }
        binding.setPinButton.setOnClickListener { showPinSetup(security) { showMessage(R.string.pin_saved) } }
        binding.removePinButton.setOnClickListener { confirmRemovePin() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.preferences.collect(::render) }
                launch { security.uiState.collect(::renderSecurity) }
            }
        }
    }

    /** Al salir de Ajustes se vuelve a bloquear con el PIN. */
    override fun onStop() {
        security.lock()
        super.onStop()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(preferences: AccessibilityPreferences) {
        val binding = binding ?: return
        if (!tracking) {
            binding.textScaleSlider.value = snap(preferences.textScale)
            binding.voiceVolumeSlider.value = snap(preferences.voiceVolume)
        }
        showTextPreview(binding.textScaleSlider.value)
        binding.voiceVolumeValue.text = percent(binding.voiceVolumeSlider.value)
        binding.voiceFeedbackSwitch.isChecked = preferences.voiceFeedback
    }

    /**
     * La app ya está dibujada con el tamaño guardado: la vista previa muestra cómo se verá
     * con el nuevo valor, en proporción al actual.
     */
    private fun showTextPreview(scale: Float) {
        val binding = binding ?: return
        val applied = viewModel.preferences.value.textScale
        val base = resources.getDimension(R.dimen.text_body) / applied
        binding.textPreview.setTextSize(TypedValue.COMPLEX_UNIT_PX, base * scale)
        binding.textScaleValue.text = percent(scale)
    }

    /** Con PIN bloqueado se muestran solo el aviso y el botón Desbloquear. */
    private fun renderSecurity(state: SecurityUiState) {
        val binding = binding ?: return
        val locked = state == SecurityUiState.LOCKED || state == SecurityUiState.LOADING
        binding.lockedCard.visibility = if (state == SecurityUiState.LOCKED) View.VISIBLE else View.GONE
        binding.settingsContent.visibility = if (locked) View.GONE else View.VISIBLE
        val hasPin = state == SecurityUiState.UNLOCKED
        binding.pinStatus.setText(if (hasPin) R.string.pin_status_on else R.string.pin_status_off)
        binding.setPinButton.setText(if (hasPin) R.string.pin_change else R.string.pin_create)
        binding.removePinButton.visibility = if (hasPin) View.VISIBLE else View.GONE
    }

    private fun confirmRemovePin() {
        requireContext().showAppDialog(
            title = getString(R.string.pin_remove_title),
            message = getString(R.string.pin_remove_message),
            positiveText = getString(R.string.pin_remove),
            negativeText = getString(R.string.button_cancel),
        ) { if (security.removePin()) showMessage(R.string.pin_removed) }
    }

    private fun showMessage(messageId: Int) {
        val root = binding?.root ?: return
        Snackbar.make(root, messageId, Snackbar.LENGTH_SHORT).show()
    }

    private fun percent(value: Float): String = getString(R.string.settings_percent, (value * 100).roundToInt())

    /** El control solo acepta valores de su escala (pasos de 0,1). */
    private fun snap(value: Float): Float = (value * 10).roundToInt() / 10f

    private fun Slider.onRelease(action: (Float) -> Unit) {
        addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                tracking = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                tracking = false
                action(slider.value)
            }
        })
    }
}
