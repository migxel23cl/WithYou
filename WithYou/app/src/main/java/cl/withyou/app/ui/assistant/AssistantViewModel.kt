package cl.withyou.app.ui.assistant

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.withyou.app.WithYouApp
import cl.withyou.app.data.prefs.PreferencesRepository
import cl.withyou.app.data.repository.ContactRepository
import cl.withyou.app.domain.model.AccessibilityPreferences
import cl.withyou.app.domain.model.ClarificationReason
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.domain.model.ContactTarget
import cl.withyou.app.domain.model.Decision
import cl.withyou.app.domain.model.ParseResult
import cl.withyou.app.domain.model.Slot
import cl.withyou.app.domain.model.TimeSpec
import cl.withyou.app.domain.model.UserIntent
import cl.withyou.app.domain.parser.ChoiceParser
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Conversación con el asistente: escuchar → interpretar → decidir → (confirmar, aclarar o
 * pedir un dato) → ejecutar → responder por voz y en pantalla a la vez.
 *
 * La comparten Inicio, Confirmar, Aclarar y Resultado (está ligada a la Activity), porque
 * una misma conversación pasa por varias pantallas. No conoce las Views ni ningún Context.
 */
class AssistantViewModel(
    private val speech: SpeechService,
    private val tts: TtsService,
    private val executor: ActionExecutor,
    private val parser: IntentParser,
    private val processor: CommandProcessor,
    private val texts: ResponseTexts,
    private val contacts: ContactRepository,
    private val preferences: PreferencesRepository,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
    private val latencyLogger: (Long) -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    /** Para el indicador "Reproduciendo confirmación…" de la pantalla Resultado. */
    val isSpeaking: StateFlow<Boolean> = tts.isSpeaking

    /** Lo que la conversación está esperando del usuario. */
    private var pending: Pending = Pending.None

    private var flashlightOn = false

    /** Contactos frecuentes, para entender "llama a María" (siempre actualizados desde Room). */
    private var knownContacts: List<Contact> = emptyList()

    /** Volumen de voz y si se responde por voz (RF-12), siempre actualizados desde DataStore. */
    private var accessibility = AccessibilityPreferences()

    /** Momento en que el usuario dejó de hablar, para medir el RNF-03 (≤ 3 s). */
    private var speechEndedAt: Long? = null

    /** Si el reconocedor deja de enviar eventos, se deja de esperar para no quedar pegado (RNF-07). */
    private var watchdog: Job? = null

    /** Vuelve solo al Inicio unos segundos después de mostrar un resultado. */
    private var resultTimer: Job? = null

    init {
        viewModelScope.launch { speech.events.collect(::onSpeechEvent) }
        viewModelScope.launch { contacts.contacts.collect { knownContacts = it } }
        viewModelScope.launch { preferences.preferences.collect { accessibility = it } }
    }

    // region Eventos de la interfaz

    fun onMicClicked() {
        if (_uiState.value.voice == VoiceState.LISTENING) {
            speech.stopListening()
            return
        }
        tts.stop()
        speechEndedAt = null
        _uiState.update {
            // En Inicio se limpia la respuesta anterior, salvo que sea una pregunta pendiente
            // ("¿Qué le quieres decir?"). En Confirmar y Aclarar siempre se mantiene la pregunta.
            val clear = it.screen == AssistantScreen.HOME && pending !is Pending.SlotFill
            it.copy(
                voice = VoiceState.LISTENING,
                partialText = "",
                heard = if (clear) null else it.heard,
                message = if (clear) null else it.message,
            )
        }
        speech.startListening()
        restartWatchdog()
    }

    /** Botón "Sí, …" de la pantalla Confirmar. */
    fun onConfirm() {
        val confirmation = pending as? Pending.Confirmation ?: return
        pending = Pending.None
        stopListeningIfNeeded()
        executeCritical(confirmation.intent)
    }

    /** Fecha de la parte superior de Inicio ("Domingo 27 de septiembre"). */
    fun todayHeader(): String = texts.todayHeader(clock().toLocalDate())

    /** Botón "Cancelar" de las pantallas Confirmar y Aclarar. */
    fun onCancel() {
        pending = Pending.None
        stopListeningIfNeeded()
        respondHome(null, texts.cancelled(), ResponseKind.INFO)
    }

    /** Opción elegida en la pantalla Aclarar. Si es una acción crítica, igual se confirma (RF-10). */
    fun onOptionSelected(index: Int) {
        val choice = pending as? Pending.Choice ?: return
        val intent = choice.options.getOrNull(index) ?: return
        pending = Pending.None
        stopListeningIfNeeded()
        apply(null, processor.decide(intent), intent)
    }

    /** Botón "Intentar de nuevo" de la pantalla Aclarar: vuelve al Inicio escuchando. */
    fun onTryAgain() {
        pending = Pending.None
        stopListeningIfNeeded()
        _uiState.value = AssistantUiState()
        onMicClicked()
    }

    /** Botón volver (barra superior o del sistema) en las pantallas del asistente. */
    fun onBack() {
        when (_uiState.value.screen) {
            AssistantScreen.CONFIRM -> onCancel()
            AssistantScreen.CLARIFY -> {
                pending = Pending.None
                stopListeningIfNeeded()
                goHome()
            }
            AssistantScreen.RESULT -> goHome()
            AssistantScreen.HOME -> Unit
        }
    }

    fun goHome() {
        resultTimer?.cancel()
        _uiState.value = AssistantUiState()
    }

    /**
     * Botón verde de llamar en la pantalla Contactos. Igual pasa por Confirmar (RF-10),
     * para evitar llamadas por un toque accidental.
     */
    fun requestCall(contact: Contact) {
        stopListeningIfNeeded()
        pending = Pending.None
        val intent = UserIntent.Call(ContactTarget.Resolved(contact))
        apply(null, processor.decide(intent), intent)
    }

    /**
     * Botón "Dictar mensaje" de la pantalla Mensajes: empieza a escuchar el texto para [contact].
     * La pregunta no se dice en voz alta, porque el micrófono ya está escuchando y la captaría.
     */
    fun dictateMessageTo(contact: Contact) {
        stopListeningIfNeeded()
        pending = Pending.SlotFill(UserIntent.SendMessage(ContactTarget.Resolved(contact)), Slot.MESSAGE_TEXT)
        _uiState.value = AssistantUiState(message = texts.askForSlot(Slot.MESSAGE_TEXT))
        onMicClicked()
    }

    /** Botón "Corregir mensaje" de Confirmar: se dicta el texto de nuevo (CU-03). */
    fun onRedictate() {
        val message = (pending as? Pending.Confirmation)?.intent as? UserIntent.SendMessage ?: return
        stopListeningIfNeeded()
        pending = Pending.SlotFill(message.copy(text = null), Slot.MESSAGE_TEXT)
        _uiState.value = AssistantUiState(message = texts.askForSlot(Slot.MESSAGE_TEXT))
        onMicClicked()
    }

    /** Acción rápida "Alarma": pregunta la hora. */
    fun onAlarmQuickAction() {
        stopListeningIfNeeded()
        val intent = UserIntent.SetAlarm(TimeSpec.MissingHour())
        pending = Pending.SlotFill(intent, Slot.TIME)
        respondHome(null, texts.askForSlot(Slot.TIME), ResponseKind.INFO)
    }

    /** Acción rápida "Linterna": la enciende o la apaga. */
    fun onFlashlightQuickAction() {
        stopListeningIfNeeded()
        pending = Pending.None
        val (message, kind) = execute(UserIntent.Flashlight(turnOn = !flashlightOn))
        respondHome(null, message, kind)
    }

    override fun onCleared() {
        speech.stopListening()
        tts.stop()
    }

    // endregion

    // region Voz

    private fun onSpeechEvent(event: SpeechEvent) {
        // Eventos tardíos de una escucha que ya terminó (p. ej. tras tocar "Cancelar") se ignoran.
        if (_uiState.value.voice != VoiceState.LISTENING) return
        when (event) {
            SpeechEvent.Ready -> restartWatchdog()
            SpeechEvent.EndOfSpeech -> {
                speechEndedAt = System.nanoTime()
                restartWatchdog()
            }
            is SpeechEvent.Partial -> {
                _uiState.update { it.copy(partialText = event.text) }
                restartWatchdog()
            }
            is SpeechEvent.Results -> onResults(event.alternatives)
            is SpeechEvent.Error -> onListeningFailed(texts.speechError(event.reason))
        }
    }

    private fun onResults(alternatives: List<String>) {
        val phrases = alternatives.filter { it.isNotBlank() }
        if (phrases.isEmpty()) {
            onListeningFailed(texts.speechError(SpeechError.NO_MATCH))
            return
        }
        when (val current = pending) {
            is Pending.Confirmation -> onConfirmationAnswer(phrases)
            is Pending.Choice -> onChoiceAnswer(phrases, current)
            is Pending.SlotFill -> onSlotAnswer(phrases, current)
            Pending.None -> onCommand(phrases)
        }
        speechEndedAt?.let { latencyLogger((System.nanoTime() - it) / 1_000_000) }
    }

    private fun onCommand(phrases: List<String>) {
        val (heard, result) = bestParse(phrases)
        _uiState.update { it.copy(voice = VoiceState.PROCESSING, heard = heard, partialText = "") }
        apply(heard, processor.process(result), result.best.intent)
    }

    /** En Confirmar solo se espera "sí" o "no" (CU-08). */
    private fun onConfirmationAnswer(phrases: List<String>) {
        val (heard, result) = bestParse(phrases)
        when (result.best.intent) {
            UserIntent.Confirm -> onConfirm()
            UserIntent.Deny -> onCancel()
            else -> askAgain(heard, texts.answerYesOrNo())
        }
    }

    /** En Aclarar se acepta "la primera", "la dos", "de la noche"… o una instrucción nueva. */
    private fun onChoiceAnswer(phrases: List<String>, choice: Pending.Choice) {
        val index = phrases.firstNotNullOfOrNull { phrase ->
            ChoiceParser.parse(phrase, choice.options.size)
                ?: if (choice.reason == ClarificationReason.AMBIGUOUS_PERIOD) ChoiceParser.parsePeriod(phrase) else null
        }
        if (index != null) {
            onOptionSelected(index)
            return
        }
        val (heard, result) = bestParse(phrases)
        when {
            result.best.intent == UserIntent.Deny -> onCancel()
            result.best.intent == UserIntent.Confirm && choice.options.size == 1 -> onOptionSelected(0)
            result.best.confidence >= CommandProcessor.HIGH_CONFIDENCE -> {
                pending = Pending.None
                apply(heard, processor.process(result), result.best.intent)
            }
            else -> askAgain(heard, texts.chooseAnOption())
        }
    }

    /** Respuesta a "¿A qué hora?" y similares. Si dice otra cosa, se toma como instrucción nueva. */
    private fun onSlotAnswer(phrases: List<String>, slotFill: Pending.SlotFill) {
        if (slotFill.slot == Slot.MESSAGE_TEXT && bestParse(phrases).second.best.intent == UserIntent.Deny) {
            onCancel()
            return
        }
        val filled = phrases.firstNotNullOfOrNull { phrase ->
            parser.completeSlot(slotFill.intent, slotFill.slot, phrase, knownContacts)?.let { phrase to it }
        }
        if (filled != null) {
            val (heard, intent) = filled
            pending = Pending.None
            apply(heard, processor.decide(intent), intent)
            return
        }
        val (heard, result) = bestParse(phrases)
        when (result.best.intent) {
            UserIntent.Deny -> onCancel()
            UserIntent.Unknown -> askAgain(heard, texts.askForSlot(slotFill.slot))
            else -> {
                pending = Pending.None
                apply(heard, processor.process(result), result.best.intent)
            }
        }
    }

    /** El reconocedor entrega hasta 3 transcripciones: se usa la que el parser entiende mejor. */
    private fun bestParse(phrases: List<String>): Pair<String, ParseResult> =
        phrases.map { it to parser.parse(it, knownContacts) }.maxBy { (_, parsed) -> parsed.best.confidence }

    private fun onListeningFailed(message: String) {
        if (_uiState.value.screen == AssistantScreen.HOME) {
            respondHome(null, message, ResponseKind.ERROR)
        } else {
            // En Confirmar o Aclarar se mantiene la pantalla: el usuario puede tocar un botón.
            watchdog?.cancel()
            _uiState.update {
                it.copy(voice = VoiceState.IDLE, partialText = "", message = message, kind = ResponseKind.ERROR)
            }
            say(message)
        }
    }

    private fun stopListeningIfNeeded() {
        if (_uiState.value.voice == VoiceState.IDLE) return
        speech.stopListening()
        watchdog?.cancel()
        _uiState.update { it.copy(voice = VoiceState.IDLE, partialText = "") }
    }

    private fun restartWatchdog() {
        watchdog?.cancel()
        watchdog = viewModelScope.launch {
            delay(LISTENING_TIMEOUT_MS)
            if (_uiState.value.voice == VoiceState.LISTENING) {
                speech.stopListening()
                onListeningFailed(texts.speechError(SpeechError.OTHER))
            }
        }
    }

    // endregion

    // region Decisiones

    private fun apply(heard: String?, decision: Decision, best: UserIntent) {
        when (decision) {
            is Decision.Execute -> {
                val (message, kind) = execute(decision.intent)
                respondHome(heard, message, kind)
            }
            is Decision.AskConfirmation -> askConfirmation(heard, decision.intent)
            is Decision.AskForSlot -> {
                pending = Pending.SlotFill(decision.intent, decision.slot)
                respondHome(heard, texts.askForSlot(decision.slot), ResponseKind.INFO)
            }
            is Decision.AskClarification -> when {
                decision.reason == ClarificationReason.NOT_UNDERSTOOD ->
                    respondHome(heard, texts.notUnderstood(), ResponseKind.INFO)
                decision.options.isEmpty() -> respondHome(heard, contactNotFound(best), ResponseKind.INFO)
                else -> askChoice(heard, decision.reason, decision.options)
            }
            is Decision.Reply -> respondHome(heard, texts.nothingToConfirm(), ResponseKind.INFO)
            is Decision.Reject -> respondHome(heard, texts.rejected(decision.reason), ResponseKind.ERROR)
        }
    }

    /** "No encontré a Roberto…" o, si aún no hay contactos, cómo agregarlos. */
    private fun contactNotFound(intent: UserIntent): String {
        val target = when (intent) {
            is UserIntent.Call -> intent.target
            is UserIntent.SendMessage -> intent.target
            else -> return texts.comingSoon(intent)
        }
        val name = (target as? ContactTarget.NotFound)?.spokenName
        return when {
            knownContacts.isEmpty() -> texts.noContacts()
            name != null -> texts.contactNotFound(name)
            else -> texts.comingSoon(intent)
        }
    }

    private fun askConfirmation(heard: String?, intent: UserIntent) {
        pending = Pending.Confirmation(intent)
        val confirmation = texts.confirmation(intent)
        show(
            AssistantUiState(
                screen = AssistantScreen.CONFIRM,
                heard = heard,
                message = confirmation.question,
                confirmation = confirmation,
                canRedictate = intent is UserIntent.SendMessage,
            ),
        )
    }

    private fun askChoice(heard: String?, reason: ClarificationReason, options: List<UserIntent>) {
        val shown = options.take(CommandProcessor.MAX_OPTIONS)
        pending = Pending.Choice(shown, reason)
        val labels = shown.map(texts::optionLabel)
        watchdog?.cancel()
        _uiState.value = AssistantUiState(
            screen = AssistantScreen.CLARIFY,
            heard = heard,
            message = texts.clarification(reason),
            options = labels,
        )
        // Se leen también las opciones, para quien no puede leer la pantalla.
        say(texts.clarificationSpoken(reason, labels))
    }

    private fun askAgain(heard: String?, message: String) {
        watchdog?.cancel()
        _uiState.update {
            it.copy(voice = VoiceState.IDLE, partialText = "", heard = heard, message = message, kind = ResponseKind.INFO)
        }
        say(message)
    }

    private fun execute(intent: UserIntent): Pair<String, ResponseKind> = when (intent) {
        UserIntent.AskTime -> texts.currentTime(clock().toLocalTime()) to ResponseKind.SUCCESS
        UserIntent.AskDate -> texts.currentDate(clock().toLocalDate()) to ResponseKind.SUCCESS
        is UserIntent.Flashlight -> when (executor.setFlashlight(intent.turnOn)) {
            ActionResult.SUCCESS -> {
                flashlightOn = intent.turnOn
                texts.flashlight(intent.turnOn) to ResponseKind.SUCCESS
            }
            ActionResult.NOT_SUPPORTED -> texts.flashlightNotSupported() to ResponseKind.ERROR
            ActionResult.AT_LIMIT, ActionResult.NEEDS_USER_STEP, ActionResult.FAILED ->
                texts.actionFailed() to ResponseKind.ERROR
        }
        is UserIntent.Volume -> when (executor.changeVolume(intent.direction)) {
            ActionResult.SUCCESS -> texts.volume(intent.direction) to ResponseKind.SUCCESS
            ActionResult.AT_LIMIT -> texts.volumeAtLimit(intent.direction) to ResponseKind.INFO
            ActionResult.NOT_SUPPORTED, ActionResult.NEEDS_USER_STEP, ActionResult.FAILED ->
                texts.actionFailed() to ResponseKind.ERROR
        }
        else -> texts.comingSoon(intent) to ResponseKind.INFO
    }

    /** Ejecuta una acción crítica ya confirmada por el usuario. */
    private fun executeCritical(intent: UserIntent) {
        when (intent) {
            is UserIntent.SetAlarm -> {
                val time = intent.time as? TimeSpec.Exact
                if (time == null) {
                    respondHome(null, texts.actionFailed(), ResponseKind.ERROR)
                    return
                }
                when (executor.setAlarm(time.hour, time.minute, texts.alarmLabel())) {
                    ActionResult.SUCCESS -> showResult(texts.alarmSet(intent))
                    ActionResult.NOT_SUPPORTED -> respondHome(null, texts.alarmNotSupported(), ResponseKind.ERROR)
                    ActionResult.AT_LIMIT, ActionResult.NEEDS_USER_STEP, ActionResult.FAILED ->
                        respondHome(null, texts.actionFailed(), ResponseKind.ERROR)
                }
            }
            is UserIntent.Call -> {
                val contact = (intent.target as? ContactTarget.Resolved)?.contact
                if (contact == null) {
                    respondHome(null, texts.actionFailed(), ResponseKind.ERROR)
                    return
                }
                when (executor.call(contact.phone)) {
                    // La llamada ya empezó: no se habla para no sonar encima de ella (se muestra igual).
                    ActionResult.SUCCESS -> showResult(texts.calling(contact.name), speak = false)
                    ActionResult.NEEDS_USER_STEP -> showResult(texts.dialerOpened(contact.name))
                    ActionResult.NOT_SUPPORTED -> respondHome(null, texts.callNotSupported(), ResponseKind.ERROR)
                    ActionResult.AT_LIMIT, ActionResult.FAILED ->
                        respondHome(null, texts.actionFailed(), ResponseKind.ERROR)
                }
            }
            is UserIntent.SendMessage -> {
                val contact = (intent.target as? ContactTarget.Resolved)?.contact
                val text = intent.text
                if (contact == null || text.isNullOrBlank()) {
                    respondHome(null, texts.actionFailed(), ResponseKind.ERROR)
                    return
                }
                when (executor.sendMessage(contact.phone, text)) {
                    ActionResult.SUCCESS, ActionResult.NEEDS_USER_STEP -> showResult(texts.messageReady(contact.name))
                    ActionResult.NOT_SUPPORTED -> respondHome(null, texts.messageNotSupported(), ResponseKind.ERROR)
                    ActionResult.AT_LIMIT, ActionResult.FAILED ->
                        respondHome(null, texts.actionFailed(), ResponseKind.ERROR)
                }
            }
            else -> respondHome(null, texts.comingSoon(intent), ResponseKind.INFO)
        }
    }

    private fun showResult(message: String, speak: Boolean = true) {
        show(
            AssistantUiState(screen = AssistantScreen.RESULT, message = message, kind = ResponseKind.SUCCESS),
            speak = speak,
        )
        resultTimer?.cancel()
        resultTimer = viewModelScope.launch {
            delay(RESULT_TIMEOUT_MS)
            if (_uiState.value.screen == AssistantScreen.RESULT) goHome()
        }
    }

    private fun respondHome(heard: String?, message: String, kind: ResponseKind) =
        show(AssistantUiState(screen = AssistantScreen.HOME, heard = heard, message = message, kind = kind))

    /** Muestra y dice el mensaje en el mismo momento (RF-13). */
    private fun show(state: AssistantUiState, speak: Boolean = true) {
        watchdog?.cancel()
        _uiState.value = state
        if (speak) state.message?.let(::say)
    }

    // endregion

    /** Habla con el volumen elegido, salvo que el usuario haya desactivado la respuesta por voz. */
    private fun say(text: String) {
        if (accessibility.voiceFeedback) tts.speak(text, accessibility.voiceVolume)
    }

    private sealed interface Pending {
        data object None : Pending
        data class Confirmation(val intent: UserIntent) : Pending
        data class Choice(val options: List<UserIntent>, val reason: ClarificationReason) : Pending
        data class SlotFill(val intent: UserIntent, val slot: Slot) : Pending
    }

    companion object {
        private const val TAG = "WithYou.Assistant"
        const val LISTENING_TIMEOUT_MS = 10_000L
        const val RESULT_TIMEOUT_MS = 8_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WithYouApp
                val container = app.container
                AssistantViewModel(
                    speech = container.speechService,
                    tts = container.ttsService,
                    executor = container.actionExecutor,
                    parser = container.intentParser,
                    processor = container.commandProcessor,
                    texts = AndroidResponseTexts(app),
                    contacts = container.contactRepository,
                    preferences = container.preferencesRepository,
                    latencyLogger = { ms -> Log.d(TAG, "Latencia fin del habla → respuesta: $ms ms") },
                )
            }
        }
    }
}
