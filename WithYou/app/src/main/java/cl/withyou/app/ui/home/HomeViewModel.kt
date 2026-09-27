package cl.withyou.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.withyou.app.WithYouApp
import cl.withyou.app.domain.model.ClarificationReason
import cl.withyou.app.domain.model.Decision
import cl.withyou.app.domain.model.UserIntent
import cl.withyou.app.domain.parser.IntentParser
import cl.withyou.app.domain.processor.CommandProcessor
import cl.withyou.app.services.ActionExecutor
import cl.withyou.app.services.ActionResult
import cl.withyou.app.services.SpeechError
import cl.withyou.app.services.SpeechEvent
import cl.withyou.app.services.SpeechService
import cl.withyou.app.services.TtsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Pantalla de Inicio: escucha, interpreta, ejecuta y responde por voz y en pantalla a la vez.
 * No conoce la View ni ningún Context; todo llega por el constructor.
 */
class HomeViewModel(
    private val speech: SpeechService,
    private val tts: TtsService,
    private val executor: ActionExecutor,
    private val parser: IntentParser,
    private val processor: CommandProcessor,
    private val texts: ResponseTexts,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
    private val latencyLogger: (Long) -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Momento en que el usuario dejó de hablar, para medir el RNF-03 (≤ 3 s). */
    private var speechEndedAt: Long? = null

    /** Si el reconocedor deja de enviar eventos, se deja de esperar para no quedar pegado (RNF-07). */
    private var watchdog: Job? = null

    init {
        viewModelScope.launch { speech.events.collect(::onSpeechEvent) }
    }

    fun onMicClicked() {
        if (_uiState.value is HomeUiState.Listening) {
            speech.stopListening()
            return
        }
        tts.stop()
        speechEndedAt = null
        _uiState.value = HomeUiState.Listening()
        speech.startListening()
        restartWatchdog()
    }

    private fun restartWatchdog() {
        watchdog?.cancel()
        watchdog = viewModelScope.launch {
            delay(LISTENING_TIMEOUT_MS)
            if (_uiState.value is HomeUiState.Listening) {
                speech.stopListening()
                respond(null, texts.speechError(SpeechError.OTHER), ResponseKind.ERROR)
            }
        }
    }

    override fun onCleared() {
        speech.stopListening()
        tts.stop()
    }

    private fun onSpeechEvent(event: SpeechEvent) {
        when (event) {
            SpeechEvent.Ready -> restartWatchdog()
            SpeechEvent.EndOfSpeech -> {
                speechEndedAt = System.nanoTime()
                restartWatchdog()
            }
            is SpeechEvent.Partial -> if (_uiState.value is HomeUiState.Listening) {
                _uiState.value = HomeUiState.Listening(event.text)
                restartWatchdog()
            }
            is SpeechEvent.Results -> onResults(event.alternatives)
            is SpeechEvent.Error -> respond(null, texts.speechError(event.reason), ResponseKind.ERROR)
        }
    }

    private fun onResults(alternatives: List<String>) {
        val phrases = alternatives.filter { it.isNotBlank() }
        if (phrases.isEmpty()) {
            respond(null, texts.speechError(SpeechError.NO_MATCH), ResponseKind.ERROR)
            return
        }
        // El reconocedor entrega hasta 3 transcripciones: se usa la que el parser entiende mejor.
        val (heard, result) = phrases
            .map { it to parser.parse(it) }
            .maxBy { (_, parsed) -> parsed.best.confidence }
        _uiState.value = HomeUiState.Processing(heard)

        val (message, kind) = handle(processor.process(result), result.best.intent)
        respond(heard, message, kind)
        speechEndedAt?.let { latencyLogger((System.nanoTime() - it) / 1_000_000) }
    }

    private fun handle(decision: Decision, best: UserIntent): Pair<String, ResponseKind> = when (decision) {
        is Decision.Execute -> execute(decision.intent)
        // Alarmas, llamadas y mensajes se implementan en el siguiente paso, con su pantalla de confirmación.
        is Decision.AskConfirmation -> texts.comingSoon(decision.intent) to ResponseKind.INFO
        is Decision.AskForSlot -> texts.comingSoon(decision.intent) to ResponseKind.INFO
        is Decision.Reject -> texts.comingSoon(decision.intent) to ResponseKind.INFO
        is Decision.AskClarification -> when (decision.reason) {
            ClarificationReason.NOT_UNDERSTOOD -> texts.notUnderstood() to ResponseKind.INFO
            ClarificationReason.LOW_CONFIDENCE -> texts.didYouMean(decision.options) to ResponseKind.INFO
            else -> texts.comingSoon(best) to ResponseKind.INFO
        }
        is Decision.Reply -> texts.nothingToConfirm() to ResponseKind.INFO
    }

    private fun execute(intent: UserIntent): Pair<String, ResponseKind> = when (intent) {
        UserIntent.AskTime -> texts.currentTime(clock().toLocalTime()) to ResponseKind.SUCCESS
        UserIntent.AskDate -> texts.currentDate(clock().toLocalDate()) to ResponseKind.SUCCESS
        is UserIntent.Flashlight -> when (executor.setFlashlight(intent.turnOn)) {
            ActionResult.SUCCESS -> texts.flashlight(intent.turnOn) to ResponseKind.SUCCESS
            ActionResult.NOT_SUPPORTED -> texts.flashlightNotSupported() to ResponseKind.ERROR
            ActionResult.AT_LIMIT, ActionResult.FAILED -> texts.actionFailed() to ResponseKind.ERROR
        }
        is UserIntent.Volume -> when (executor.changeVolume(intent.direction)) {
            ActionResult.SUCCESS -> texts.volume(intent.direction) to ResponseKind.SUCCESS
            ActionResult.AT_LIMIT -> texts.volumeAtLimit(intent.direction) to ResponseKind.INFO
            ActionResult.NOT_SUPPORTED, ActionResult.FAILED -> texts.actionFailed() to ResponseKind.ERROR
        }
        else -> texts.comingSoon(intent) to ResponseKind.INFO
    }

    /** Muestra y dice la respuesta en el mismo momento (RF-13). */
    private fun respond(heard: String?, message: String, kind: ResponseKind) {
        watchdog?.cancel()
        _uiState.value = HomeUiState.Responded(heard, message, kind)
        tts.speak(message)
    }

    companion object {
        private const val TAG = "WithYou.Home"
        const val LISTENING_TIMEOUT_MS = 10_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WithYouApp
                val container = app.container
                HomeViewModel(
                    speech = container.speechService,
                    tts = container.ttsService,
                    executor = container.actionExecutor,
                    parser = container.intentParser,
                    processor = container.commandProcessor,
                    texts = AndroidResponseTexts(app),
                    latencyLogger = { ms -> Log.d(TAG, "Latencia fin del habla → respuesta: $ms ms") },
                )
            }
        }
    }
}
