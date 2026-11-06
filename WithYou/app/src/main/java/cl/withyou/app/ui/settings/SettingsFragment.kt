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
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Pantalla Ajustes (RF-12, mockup 7): tamaño del texto con vista previa, volumen de la voz y
 * respuesta por voz. Los cambios se guardan al soltar el control.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var binding: FragmentSettingsBinding? = null

    /** true mientras el usuario arrastra un control: así no se le mueve desde [render]. */
    private var tracking = false

    /** Compartido con MainActivity, que aplica el tamaño del texto a toda la app. */
    private val viewModel: SettingsViewModel by activityViewModels { SettingsViewModel.Factory }

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.preferences.collect(::render)
            }
        }
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
