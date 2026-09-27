package cl.withyou.app.ui.home

import cl.withyou.app.domain.model.ContactTarget
import cl.withyou.app.domain.model.UserIntent
import cl.withyou.app.domain.model.VolumeDirection
import cl.withyou.app.domain.parser.IntentParser
import cl.withyou.app.domain.processor.CommandProcessor
import cl.withyou.app.services.ActionExecutor
import cl.withyou.app.services.ActionResult
import cl.withyou.app.services.SpeechError
import cl.withyou.app.services.SpeechEvent
import cl.withyou.app.services.SpeechService
import cl.withyou.app.services.TtsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val speech = FakeSpeechService()
    private val tts = FakeTtsService()
    private val executor = FakeActionExecutor()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = HomeViewModel(
            speech = speech,
            tts = tts,
            executor = executor,
            parser = IntentParser(),
            processor = CommandProcessor { NOW },
            texts = FakeTexts(),
            clock = { NOW },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Simula que el usuario toca el micrófono y el reconocedor devuelve estas transcripciones. */
    private fun say(vararg alternatives: String): HomeUiState.Responded {
        viewModel.onMicClicked()
        speech.emit(SpeechEvent.EndOfSpeech)
        speech.emit(SpeechEvent.Results(alternatives.toList()))
        return viewModel.uiState.value as HomeUiState.Responded
    }

    @Test
    fun `tocar el microfono empieza a escuchar y detiene la voz`() {
        viewModel.onMicClicked()
        assertEquals(1, speech.startCount)
        assertEquals(1, tts.stopCount)
        assertEquals(HomeUiState.Listening(), viewModel.uiState.value)

        speech.emit(SpeechEvent.Partial("qué ho"))
        assertEquals(HomeUiState.Listening("qué ho"), viewModel.uiState.value)
    }

    @Test
    fun `tocar mientras escucha termina de escuchar`() {
        viewModel.onMicClicked()
        viewModel.onMicClicked()
        assertEquals(1, speech.startCount)
        assertEquals(1, speech.stopCount)
    }

    @Test
    fun `pregunta la hora - pantalla y voz dicen lo mismo`() {
        val state = say("qué hora es")
        assertEquals(HomeUiState.Responded("qué hora es", "hora 10:25", ResponseKind.SUCCESS), state)
        assertEquals(listOf("hora 10:25"), tts.spoken)
    }

    @Test
    fun `pregunta la fecha`() {
        assertEquals("fecha 2026-09-27", say("qué día es hoy").message)
    }

    @Test
    fun `enciende la linterna`() {
        val state = say("enciende la linterna")
        assertEquals(listOf(true), executor.flashlightCalls)
        assertEquals(HomeUiState.Responded("enciende la linterna", "linterna true", ResponseKind.SUCCESS), state)
    }

    @Test
    fun `telefono sin linterna avisa el error por voz`() {
        executor.flashlightResult = ActionResult.NOT_SUPPORTED
        val state = say("prende la linterna")
        assertEquals(ResponseKind.ERROR, state.kind)
        assertEquals(listOf("sin linterna"), tts.spoken)
    }

    @Test
    fun `sube el volumen y avisa si ya esta al maximo`() {
        assertEquals("volumen UP", say("sube el volumen").message)
        executor.volumeResult = ActionResult.AT_LIMIT
        assertEquals("volumen al limite UP", say("sube el volumen").message)
        assertEquals(listOf(VolumeDirection.UP, VolumeDirection.UP), executor.volumeCalls)
    }

    @Test
    fun `frase sin sentido responde con ayuda, nunca en silencio`() {
        val state = say("el perro está en el patio")
        assertEquals("no entendi", state.message)
        assertEquals(listOf("no entendi"), tts.spoken)
    }

    @Test
    fun `elige la transcripcion que mejor se entiende`() {
        val state = say("que ora es", "qué hora es")
        assertEquals("qué hora es", state.heard)
        assertEquals("hora 10:25", state.message)
    }

    @Test
    fun `error del reconocedor da un mensaje amable`() {
        viewModel.onMicClicked()
        speech.emit(SpeechEvent.Error(SpeechError.NO_SPEECH))
        assertEquals(HomeUiState.Responded(null, "error NO_SPEECH", ResponseKind.ERROR), viewModel.uiState.value)
        assertEquals(listOf("error NO_SPEECH"), tts.spoken)
    }

    @Test
    fun `si el reconocedor no responde deja de esperar y avisa`() = runTest(dispatcher) {
        viewModel.onMicClicked()
        advanceTimeBy(HomeViewModel.LISTENING_TIMEOUT_MS + 1)
        assertEquals(HomeUiState.Responded(null, "error OTHER", ResponseKind.ERROR), viewModel.uiState.value)
        assertEquals(1, speech.stopCount)
        assertEquals(listOf("error OTHER"), tts.spoken)
    }

    @Test
    fun `mientras llegan eventos sigue escuchando`() = runTest(dispatcher) {
        viewModel.onMicClicked()
        advanceTimeBy(HomeViewModel.LISTENING_TIMEOUT_MS - 1_000)
        speech.emit(SpeechEvent.Partial("qué hora"))
        advanceTimeBy(HomeViewModel.LISTENING_TIMEOUT_MS - 1_000)
        assertEquals(HomeUiState.Listening("qué hora"), viewModel.uiState.value)
    }

    @Test
    fun `el vigilante no interrumpe una respuesta ya dada`() = runTest(dispatcher) {
        say("qué hora es")
        advanceTimeBy(HomeViewModel.LISTENING_TIMEOUT_MS * 2)
        assertEquals(listOf("hora 10:25"), tts.spoken)
    }

    @Test
    fun `resultados vacios cuentan como no escuchado`() {
        assertEquals("error NO_MATCH", say("", "  ").message)
    }

    @Test
    fun `llamar aun no esta disponible y lo dice`() {
        val state = say("llama a María")
        assertEquals("pronto Call(target=${ContactTarget.NotFound("María")})", state.message)
        assertEquals(ResponseKind.INFO, state.kind)
        assertTrue(executor.flashlightCalls.isEmpty())
    }

    @Test
    fun `un si sin pregunta pendiente no hace nada peligroso`() {
        assertEquals("nada que confirmar", say("sí").message)
    }

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 27, 10, 25)
    }
}

private class FakeSpeechService : SpeechService {
    private val flow = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events = flow
    var startCount = 0
    var stopCount = 0

    override fun startListening() {
        startCount++
    }

    override fun stopListening() {
        stopCount++
    }

    fun emit(event: SpeechEvent) {
        check(flow.tryEmit(event))
    }
}

private class FakeTtsService : TtsService {
    override val isSpeaking: StateFlow<Boolean> = MutableStateFlow(false)
    val spoken = mutableListOf<String>()
    var stopCount = 0

    override fun speak(text: String) {
        spoken += text
    }

    override fun stop() {
        stopCount++
    }
}

private class FakeActionExecutor : ActionExecutor {
    var flashlightResult = ActionResult.SUCCESS
    var volumeResult = ActionResult.SUCCESS
    val flashlightCalls = mutableListOf<Boolean>()
    val volumeCalls = mutableListOf<VolumeDirection>()

    override fun setFlashlight(on: Boolean): ActionResult {
        flashlightCalls += on
        return flashlightResult
    }

    override fun changeVolume(direction: VolumeDirection): ActionResult {
        volumeCalls += direction
        return volumeResult
    }
}

private class FakeTexts : ResponseTexts {
    override fun currentTime(time: LocalTime) = "hora $time"
    override fun currentDate(date: LocalDate) = "fecha $date"
    override fun flashlight(on: Boolean) = "linterna $on"
    override fun flashlightNotSupported() = "sin linterna"
    override fun volume(direction: VolumeDirection) = "volumen $direction"
    override fun volumeAtLimit(direction: VolumeDirection) = "volumen al limite $direction"
    override fun actionFailed() = "fallo"
    override fun notUnderstood() = "no entendi"
    override fun didYouMean(options: List<UserIntent>) = "quisiste decir $options"
    override fun comingSoon(intent: UserIntent) = "pronto $intent"
    override fun nothingToConfirm() = "nada que confirmar"
    override fun speechError(error: SpeechError) = "error $error"
}
