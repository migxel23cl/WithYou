package cl.withyou.app.ui.settings

import cl.withyou.app.data.prefs.InMemoryPreferencesRepository
import cl.withyou.app.domain.model.AccessibilityPreferences
import cl.withyou.app.services.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val repository = InMemoryPreferencesRepository()
    private val tts = RecordingTts()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(repository, tts)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `parte con los valores por defecto`() {
        assertEquals(AccessibilityPreferences(), viewModel.preferences.value)
    }

    @Test
    fun `guarda cada preferencia`() {
        viewModel.setTextScale(1.3f)
        viewModel.setVoiceVolume(0.6f)
        viewModel.setVoiceFeedback(false)
        assertEquals(AccessibilityPreferences(textScale = 1.3f, voiceVolume = 0.6f, voiceFeedback = false), viewModel.preferences.value)
    }

    @Test
    fun `nunca deja el texto mas chico que el normal ni la voz en silencio`() {
        viewModel.setTextScale(0.5f)
        viewModel.setVoiceVolume(0f)
        assertEquals(AccessibilityPreferences.MIN_TEXT_SCALE, viewModel.preferences.value.textScale)
        assertEquals(AccessibilityPreferences.MIN_VOICE_VOLUME, viewModel.preferences.value.voiceVolume)
    }

    @Test
    fun `probar la voz usa el volumen elegido`() {
        viewModel.previewVoice("prueba", volume = 0.4f)
        assertEquals(listOf("prueba" to 0.4f), tts.spoken)
    }

    @Test
    fun `el tamano de texto se informa solo cuando cambia`() = runTest {
        viewModel.setTextScale(1.2f)
        assertEquals(1.2f, viewModel.textScale.first())
    }
}

private class RecordingTts : TtsService {
    override val isSpeaking: StateFlow<Boolean> = MutableStateFlow(false)
    val spoken = mutableListOf<Pair<String, Float>>()

    override fun speak(text: String, volume: Float) {
        spoken += text to volume
    }

    override fun stop() = Unit
}
