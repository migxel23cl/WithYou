package cl.withyou.app.ui.assistant

import cl.withyou.app.data.prefs.InMemoryPreferencesRepository
import cl.withyou.app.domain.model.AccessibilityPreferences
import cl.withyou.app.domain.model.AlarmDay
import cl.withyou.app.domain.model.ClarificationReason
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.domain.model.ContactTarget
import cl.withyou.app.domain.model.RejectReason
import cl.withyou.app.domain.model.Slot
import cl.withyou.app.domain.model.TimeSpec
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
import cl.withyou.app.ui.contacts.InMemoryContactRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val speech = FakeSpeechService()
    private val tts = FakeTtsService()
    private val executor = FakeActionExecutor()
    private val contactRepository = InMemoryContactRepository()
    private val preferences = InMemoryPreferencesRepository()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: AssistantViewModel

    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = AssistantViewModel(
            speech = speech,
            tts = tts,
            executor = executor,
            parser = IntentParser(),
            processor = CommandProcessor { NOW },
            texts = FakeTexts(),
            contacts = contactRepository,
            preferences = preferences,
            clock = { NOW },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Simula que el usuario toca el micrófono y el reconocedor devuelve estas transcripciones. */
    private fun say(vararg alternatives: String): AssistantUiState {
        viewModel.onMicClicked()
        speech.emit(SpeechEvent.EndOfSpeech)
        speech.emit(SpeechEvent.Results(alternatives.toList()))
        return state
    }

    private fun homeResponse(heard: String?, message: String, kind: ResponseKind) =
        AssistantUiState(screen = AssistantScreen.HOME, heard = heard, message = message, kind = kind)

    @Test
    fun `la fecha de inicio la entrega el ViewModel`() {
        assertEquals("encabezado 2026-09-27", viewModel.todayHeader())
    }

    // region Escuchar

    @Test
    fun `tocar el microfono empieza a escuchar y detiene la voz`() {
        viewModel.onMicClicked()
        assertEquals(1, speech.startCount)
        assertEquals(1, tts.stopCount)
        assertEquals(VoiceState.LISTENING, state.voice)

        speech.emit(SpeechEvent.Partial("qué ho"))
        assertEquals("qué ho", state.partialText)
    }

    @Test
    fun `tocar mientras escucha termina de escuchar`() {
        viewModel.onMicClicked()
        viewModel.onMicClicked()
        assertEquals(1, speech.startCount)
        assertEquals(1, speech.stopCount)
    }

    @Test
    fun `elige la transcripcion que mejor se entiende`() {
        val result = say("que ora es", "qué hora es")
        assertEquals("qué hora es", result.heard)
        assertEquals("hora 10:25", result.message)
    }

    @Test
    fun `error del reconocedor da un mensaje amable`() {
        viewModel.onMicClicked()
        speech.emit(SpeechEvent.Error(SpeechError.NO_SPEECH))
        assertEquals(homeResponse(null, "error NO_SPEECH", ResponseKind.ERROR), state)
        assertEquals(listOf("error NO_SPEECH"), tts.spoken)
    }

    @Test
    fun `resultados vacios cuentan como no escuchado`() {
        assertEquals("error NO_MATCH", say("", "  ").message)
    }

    @Test
    fun `eventos tardios de una escucha terminada se ignoran`() {
        speech.emit(SpeechEvent.Results(listOf("enciende la linterna")))
        assertEquals(AssistantUiState(), state)
        assertTrue(executor.flashlightCalls.isEmpty())
    }

    @Test
    fun `si el reconocedor no responde deja de esperar y avisa`() = runTest(dispatcher) {
        viewModel.onMicClicked()
        advanceTimeBy(AssistantViewModel.LISTENING_TIMEOUT_MS + 1)
        assertEquals(homeResponse(null, "error OTHER", ResponseKind.ERROR), state)
        assertEquals(1, speech.stopCount)
    }

    @Test
    fun `mientras llegan eventos sigue escuchando`() = runTest(dispatcher) {
        viewModel.onMicClicked()
        advanceTimeBy(AssistantViewModel.LISTENING_TIMEOUT_MS - 1_000)
        speech.emit(SpeechEvent.Partial("qué hora"))
        advanceTimeBy(AssistantViewModel.LISTENING_TIMEOUT_MS - 1_000)
        assertEquals(VoiceState.LISTENING, state.voice)
    }

    // endregion

    // region Acciones no críticas (se ejecutan sin confirmar)

    @Test
    fun `pregunta la hora - pantalla y voz dicen lo mismo`() {
        assertEquals(homeResponse("qué hora es", "hora 10:25", ResponseKind.SUCCESS), say("qué hora es"))
        assertEquals(listOf("hora 10:25"), tts.spoken)
    }

    @Test
    fun `pregunta la fecha`() {
        assertEquals("fecha 2026-09-27", say("qué día es hoy").message)
    }

    @Test
    fun `enciende la linterna sin pedir confirmacion`() {
        assertEquals(homeResponse("enciende la linterna", "linterna true", ResponseKind.SUCCESS), say("enciende la linterna"))
        assertEquals(listOf(true), executor.flashlightCalls)
    }

    @Test
    fun `telefono sin linterna avisa el error por voz`() {
        executor.flashlightResult = ActionResult.NOT_SUPPORTED
        assertEquals(ResponseKind.ERROR, say("prende la linterna").kind)
        assertEquals(listOf("sin linterna"), tts.spoken)
    }

    @Test
    fun `sube el volumen y avisa si ya esta al maximo`() {
        assertEquals("volumen UP", say("sube el volumen").message)
        executor.volumeResult = ActionResult.AT_LIMIT
        assertEquals("volumen al limite UP", say("sube el volumen").message)
    }

    @Test
    fun `accion rapida de linterna la enciende y la apaga`() {
        viewModel.onFlashlightQuickAction()
        viewModel.onFlashlightQuickAction()
        assertEquals(listOf(true, false), executor.flashlightCalls)
        assertEquals("linterna false", state.message)
    }

    @Test
    fun `frase sin sentido responde con ayuda, nunca en silencio`() {
        assertEquals("no entendi", say("el perro está en el patio").message)
        assertEquals(listOf("no entendi"), tts.spoken)
    }

    @Test
    fun `un si sin pregunta pendiente no hace nada peligroso`() {
        assertEquals("nada que confirmar", say("sí").message)
    }

    @Test
    fun `sin contactos explica como agregarlos`() {
        val result = say("llama a María")
        assertEquals("sin contactos", result.message)
        assertEquals(AssistantScreen.HOME, result.screen)
    }

    // endregion

    // region Alarma con confirmación (CU-01, CU-08)

    private val alarmTomorrow8 = UserIntent.SetAlarm(TimeSpec.Exact(8, 0), AlarmDay.TOMORROW)

    @Test
    fun `una alarma pide confirmacion antes de crearse`() {
        val result = say("pon una alarma para mañana a las ocho de la mañana")
        assertEquals(AssistantScreen.CONFIRM, result.screen)
        assertEquals("¿$alarmTomorrow8?", result.confirmation?.title)
        assertEquals("¿$alarmTomorrow8?", result.message)
        assertEquals(listOf("¿$alarmTomorrow8?"), tts.spoken)
        assertTrue(executor.alarmCalls.isEmpty())
    }

    @Test
    fun `al confirmar con el boton se crea la alarma y se muestra el resultado`() {
        say("pon una alarma para mañana a las ocho de la mañana")
        viewModel.onConfirm()
        assertEquals(listOf(Triple(8, 0, "Withyou")), executor.alarmCalls)
        assertEquals(
            AssistantUiState(screen = AssistantScreen.RESULT, message = "alarma $alarmTomorrow8", kind = ResponseKind.SUCCESS),
            state,
        )
    }

    @Test
    fun `se puede confirmar diciendo si`() {
        say("pon una alarma para mañana a las ocho de la mañana")
        say("sí, por favor")
        assertEquals(1, executor.alarmCalls.size)
        assertEquals(AssistantScreen.RESULT, state.screen)
    }

    @Test
    fun `decir no cancela y vuelve al inicio sin crear nada`() {
        say("pon una alarma para mañana a las ocho de la mañana")
        say("no")
        assertTrue(executor.alarmCalls.isEmpty())
        assertEquals(homeResponse(null, "cancelado", ResponseKind.INFO), state)
    }

    @Test
    fun `el boton cancelar y el boton volver cancelan`() {
        say("pon una alarma para mañana a las ocho de la mañana")
        viewModel.onCancel()
        assertEquals("cancelado", state.message)

        say("pon una alarma para mañana a las ocho de la mañana")
        viewModel.onBack()
        assertEquals(AssistantScreen.HOME, state.screen)
        assertTrue(executor.alarmCalls.isEmpty())
    }

    @Test
    fun `en confirmar otra respuesta pide si o no y mantiene la pantalla`() {
        say("pon una alarma para mañana a las ocho de la mañana")
        val result = say("qué hora es")
        assertEquals(AssistantScreen.CONFIRM, result.screen)
        assertEquals("di si o no", result.message)
        assertTrue(executor.alarmCalls.isEmpty())
    }

    @Test
    fun `un error al escuchar en confirmar no saca de la pantalla`() {
        say("pon una alarma para mañana a las ocho de la mañana")
        viewModel.onMicClicked()
        speech.emit(SpeechEvent.Error(SpeechError.NO_SPEECH))
        assertEquals(AssistantScreen.CONFIRM, state.screen)
        assertEquals("error NO_SPEECH", state.message)
    }

    @Test
    fun `confirmar dos veces no crea dos alarmas`() {
        say("pon una alarma para mañana a las ocho de la mañana")
        viewModel.onConfirm()
        viewModel.onConfirm()
        assertEquals(1, executor.alarmCalls.size)
    }

    @Test
    fun `sin app de reloj lo avisa`() {
        executor.alarmResult = ActionResult.NOT_SUPPORTED
        say("pon una alarma para mañana a las ocho de la mañana")
        viewModel.onConfirm()
        assertEquals(homeResponse(null, "sin reloj", ResponseKind.ERROR), state)
    }

    @Test
    fun `el resultado vuelve solo al inicio`() = runTest(dispatcher) {
        say("pon una alarma para mañana a las ocho de la mañana")
        viewModel.onConfirm()
        advanceTimeBy(AssistantViewModel.RESULT_TIMEOUT_MS + 1)
        assertEquals(AssistantUiState(), state)
    }

    @Test
    fun `una hora que ya paso se rechaza con explicacion`() {
        val result = say("pon una alarma para hoy a las siete de la mañana")
        assertEquals(homeResponse("pon una alarma para hoy a las siete de la mañana", "rechazo TIME_ALREADY_PASSED", ResponseKind.ERROR), result)
    }

    // endregion

    // region Aclaración (CU-06)

    private val alarm8 = UserIntent.SetAlarm(TimeSpec.Exact(8, 0))
    private val alarm20 = UserIntent.SetAlarm(TimeSpec.Exact(20, 0))

    @Test
    fun `alarma sin mañana o noche muestra las dos opciones y las lee`() {
        val result = say("pon una alarma a las ocho")
        assertEquals(AssistantScreen.CLARIFY, result.screen)
        assertEquals("aclara AMBIGUOUS_PERIOD", result.message)
        assertEquals(listOf("opcion $alarm8", "opcion $alarm20"), result.options)
        assertEquals(listOf("aclara AMBIGUOUS_PERIOD [opcion $alarm8, opcion $alarm20]"), tts.spoken)
    }

    @Test
    fun `elegir una opcion lleva a confirmar`() {
        say("pon una alarma a las ocho")
        viewModel.onOptionSelected(1)
        assertEquals(AssistantScreen.CONFIRM, state.screen)
        assertEquals("¿${alarm20.copy(day = AlarmDay.TODAY)}?", state.confirmation?.title)
    }

    @Test
    fun `se puede elegir por voz con el numero o el momento del dia`() {
        say("pon una alarma a las ocho")
        say("la primera")
        assertEquals("¿${alarm8.copy(day = AlarmDay.TOMORROW)}?", state.confirmation?.title)

        viewModel.onCancel()
        say("pon una alarma a las ocho")
        say("de la noche")
        assertEquals("¿${alarm20.copy(day = AlarmDay.TODAY)}?", state.confirmation?.title)
    }

    @Test
    fun `en aclarar se puede dar una instruccion nueva`() {
        say("pon una alarma a las ocho")
        assertEquals(homeResponse("qué hora es", "hora 10:25", ResponseKind.SUCCESS), say("qué hora es"))
    }

    @Test
    fun `en aclarar una respuesta confusa pide elegir de nuevo`() {
        say("pon una alarma a las ocho")
        val result = say("bla bla")
        assertEquals(AssistantScreen.CLARIFY, result.screen)
        assertEquals("elige", result.message)
    }

    @Test
    fun `cancelar en aclarar vuelve al inicio sin hacer nada`() {
        say("pon una alarma a las ocho")
        viewModel.onCancel()
        assertEquals(homeResponse(null, "cancelado", ResponseKind.INFO), state)
        viewModel.onOptionSelected(0)
        assertEquals(AssistantScreen.HOME, state.screen)
        assertTrue(executor.alarmCalls.isEmpty())
    }

    @Test
    fun `intentar de nuevo vuelve al inicio escuchando`() {
        say("pon una alarma a las ocho")
        viewModel.onTryAgain()
        assertEquals(AssistantScreen.HOME, state.screen)
        assertEquals(VoiceState.LISTENING, state.voice)
    }

    @Test
    fun `baja confianza ofrece las opciones posibles`() {
        val result = say("a las ocho de la mañana")
        assertEquals(AssistantScreen.CLARIFY, result.screen)
        assertEquals("aclara ${ClarificationReason.LOW_CONFIDENCE}", result.message)
    }

    // endregion

    // region Dato faltante

    @Test
    fun `alarma sin hora pregunta la hora y la completa con la respuesta`() {
        val question = say("pon una alarma")
        assertEquals(homeResponse("pon una alarma", "falta ${Slot.TIME}", ResponseKind.INFO), question)

        val result = say("mañana a las siete de la mañana")
        assertEquals(AssistantScreen.CONFIRM, result.screen)
        assertEquals("¿${UserIntent.SetAlarm(TimeSpec.Exact(7, 0), AlarmDay.TOMORROW)}?", result.confirmation?.title)
    }

    @Test
    fun `alarma en la tarde mas a las cinco son las 17`() {
        say("pon una alarma en la tarde")
        val result = say("a las cinco")
        assertEquals("¿${UserIntent.SetAlarm(TimeSpec.Exact(17, 0), AlarmDay.TODAY)}?", result.confirmation?.title)
    }

    @Test
    fun `accion rapida de alarma pregunta la hora`() {
        viewModel.onAlarmQuickAction()
        assertEquals("falta ${Slot.TIME}", state.message)
        say("a las nueve de la noche")
        assertEquals(AssistantScreen.CONFIRM, state.screen)
    }

    @Test
    fun `si no entiende la hora la vuelve a preguntar`() {
        say("pon una alarma")
        val result = say("eh")
        assertEquals("falta ${Slot.TIME}", result.message)
        say("a las nueve de la noche")
        assertEquals(AssistantScreen.CONFIRM, state.screen)
    }

    @Test
    fun `en vez de la hora puede pedir otra cosa`() {
        say("pon una alarma")
        assertEquals("linterna true", say("enciende la linterna").message)
        assertNull(executor.alarmCalls.firstOrNull())
    }

    // endregion

    // region Llamadas (CU-02)

    private val pedro = Contact(1, "Pedro Soto", "+56922222222")
    private val maria = Contact(2, "María González", "+56911111111")
    private val mariaPerez = Contact(3, "María Pérez", "+56933333333")

    private fun withContacts(vararg contacts: Contact) {
        contactRepository.contacts.value = contacts.sortedBy { it.name }
    }

    private fun callTo(contact: Contact) = UserIntent.Call(ContactTarget.Resolved(contact))

    @Test
    fun `llamar pide confirmacion y luego llama`() {
        withContacts(pedro, maria)
        val confirm = say("llama a Pedro")
        assertEquals(AssistantScreen.CONFIRM, confirm.screen)
        assertEquals("¿${callTo(pedro)}?", confirm.confirmation?.title)
        assertTrue(executor.callCalls.isEmpty())

        viewModel.onConfirm()
        assertEquals(listOf("+56922222222"), executor.callCalls)
        assertEquals(
            AssistantUiState(screen = AssistantScreen.RESULT, message = "llamando Pedro Soto", kind = ResponseKind.SUCCESS),
            state,
        )
    }

    @Test
    fun `durante la llamada no se habla para no sonar encima`() {
        withContacts(pedro)
        say("llama a Pedro")
        viewModel.onConfirm()
        assertEquals(listOf("¿${callTo(pedro)}?"), tts.spoken)
    }

    @Test
    fun `sin permiso de llamadas abre el telefono y lo explica`() {
        withContacts(pedro)
        executor.callResult = ActionResult.NEEDS_USER_STEP
        say("llama a Pedro")
        viewModel.onConfirm()
        assertEquals("telefono abierto Pedro Soto", state.message)
        assertEquals("telefono abierto Pedro Soto", tts.spoken.last())
    }

    @Test
    fun `dos contactos con el mismo nombre piden elegir`() {
        withContacts(pedro, maria, mariaPerez)
        val result = say("llama a María")
        assertEquals(AssistantScreen.CLARIFY, result.screen)
        assertEquals("aclara ${ClarificationReason.AMBIGUOUS_CONTACT}", result.message)
        assertEquals(listOf("opcion ${callTo(maria)}", "opcion ${callTo(mariaPerez)}"), result.options)

        say("la segunda")
        assertEquals("¿${callTo(mariaPerez)}?", state.confirmation?.title)
    }

    @Test
    fun `un nombre que no esta en contactos lo dice`() {
        withContacts(pedro, maria)
        assertEquals("no encontre Roberto", say("llama a Roberto").message)
    }

    @Test
    fun `si no dice a quien lo pregunta`() {
        withContacts(pedro)
        assertEquals("falta ${Slot.CONTACT}", say("quiero llamar").message)
        assertEquals("¿${callTo(pedro)}?", say("a Pedro").confirmation?.title)
    }

    @Test
    fun `el boton verde de contactos tambien confirma antes de llamar`() {
        viewModel.requestCall(pedro)
        assertEquals(AssistantScreen.CONFIRM, state.screen)
        assertTrue(executor.callCalls.isEmpty())
    }

    @Test
    fun `un telefono sin llamadas lo avisa`() {
        withContacts(pedro)
        executor.callResult = ActionResult.NOT_SUPPORTED
        say("llama a Pedro")
        viewModel.onConfirm()
        assertEquals(homeResponse(null, "sin llamadas", ResponseKind.ERROR), state)
    }

    // endregion

    // region Mensajes (CU-03)

    private fun messageTo(contact: Contact, text: String) = UserIntent.SendMessage(ContactTarget.Resolved(contact), text)

    /** El reconocedor entrega lo dictado sin que se vuelva a tocar el micrófono (ya está escuchando). */
    private fun dictate(text: String): AssistantUiState {
        speech.emit(SpeechEvent.Results(listOf(text)))
        return state
    }

    @Test
    fun `pide el texto, lo lee en voz alta y lo confirma antes de enviarlo`() {
        withContacts(pedro)
        assertEquals(
            homeResponse("mándale un mensaje a Pedro", "falta ${Slot.MESSAGE_TEXT}", ResponseKind.INFO),
            say("mándale un mensaje a Pedro"),
        )

        val confirm = say("llego a las seis")
        assertEquals(AssistantScreen.CONFIRM, confirm.screen)
        assertEquals("¿${messageTo(pedro, "Llego a las seis")}?", confirm.confirmation?.title)
        assertEquals("¿${messageTo(pedro, "Llego a las seis")}?", tts.spoken.last())
        assertTrue(confirm.canRedictate)
        assertTrue(executor.messageCalls.isEmpty())

        viewModel.onConfirm()
        assertEquals(listOf("+56922222222" to "Llego a las seis"), executor.messageCalls)
        assertEquals(
            AssistantUiState(screen = AssistantScreen.RESULT, message = "mensaje listo Pedro Soto", kind = ResponseKind.SUCCESS),
            state,
        )
    }

    @Test
    fun `envia un mensaje sin nombre pregunta a quien y luego el texto`() {
        withContacts(pedro)
        assertEquals("falta ${Slot.CONTACT}", say("envía un mensaje").message)
        assertEquals("falta ${Slot.MESSAGE_TEXT}", say("a Pedro").message)
        assertEquals("¿${messageTo(pedro, "Ya voy")}?", say("ya voy").confirmation?.title)
    }

    @Test
    fun `con dile a que el texto viene en la misma frase`() {
        withContacts(pedro)
        val confirm = say("dile a Pedro que llego tarde")
        assertEquals("¿${messageTo(pedro, "Llego tarde")}?", confirm.confirmation?.title)
    }

    @Test
    fun `la pregunta sigue visible mientras se dicta`() {
        withContacts(pedro)
        say("mándale un mensaje a Pedro")
        viewModel.onMicClicked()
        assertEquals(VoiceState.LISTENING, state.voice)
        assertEquals("falta ${Slot.MESSAGE_TEXT}", state.message)
    }

    @Test
    fun `se puede corregir el mensaje dictandolo de nuevo`() {
        withContacts(pedro)
        say("dile a Pedro que llego tard")
        viewModel.onRedictate()
        assertEquals(AssistantScreen.HOME, state.screen)
        assertEquals(VoiceState.LISTENING, state.voice)

        val confirm = dictate("llego tarde, guárdame comida")
        assertEquals("¿${messageTo(pedro, "Llego tarde, guárdame comida")}?", confirm.confirmation?.title)
        viewModel.onConfirm()
        assertEquals(listOf("+56922222222" to "Llego tarde, guárdame comida"), executor.messageCalls)
    }

    @Test
    fun `desde la pantalla Mensajes se dicta al contacto elegido`() {
        viewModel.dictateMessageTo(pedro)
        assertEquals(VoiceState.LISTENING, state.voice)
        assertEquals("falta ${Slot.MESSAGE_TEXT}", state.message)
        assertTrue("la pregunta no se dice: el micrófono la captaría", tts.spoken.isEmpty())

        assertEquals("¿${messageTo(pedro, "Hola, ¿cómo estás?")}?", dictate("hola, ¿cómo estás?").confirmation?.title)
    }

    @Test
    fun `decir no mientras se dicta cancela`() {
        viewModel.dictateMessageTo(pedro)
        assertEquals(homeResponse(null, "cancelado", ResponseKind.INFO), dictate("no"))
        assertTrue(executor.messageCalls.isEmpty())
    }

    @Test
    fun `sin app de mensajes lo avisa`() {
        withContacts(pedro)
        executor.messageResult = ActionResult.NOT_SUPPORTED
        say("dile a Pedro que llego tarde")
        viewModel.onConfirm()
        assertEquals(homeResponse(null, "sin mensajes", ResponseKind.ERROR), state)
    }

    @Test
    fun `corregir solo aparece en mensajes`() {
        withContacts(pedro)
        say("llama a Pedro")
        assertEquals(false, state.canRedictate)
        viewModel.onRedictate()
        assertEquals(AssistantScreen.CONFIRM, state.screen)
    }

    // endregion

    // region Preferencias de voz (RF-12)

    @Test
    fun `habla con el volumen elegido en Ajustes`() {
        preferences.preferences.value = AccessibilityPreferences(voiceVolume = 0.5f)
        say("qué hora es")
        assertEquals(listOf(0.5f), tts.volumes)
    }

    @Test
    fun `sin respuesta por voz solo muestra en pantalla`() {
        preferences.preferences.value = AccessibilityPreferences(voiceFeedback = false)
        assertEquals("hora 10:25", say("qué hora es").message)
        assertTrue(tts.spoken.isEmpty())
    }

    @Test
    fun `sin respuesta por voz la confirmacion igual se muestra`() {
        preferences.preferences.value = AccessibilityPreferences(voiceFeedback = false)
        val confirm = say("pon una alarma para mañana a las ocho de la mañana")
        assertEquals(AssistantScreen.CONFIRM, confirm.screen)
        assertTrue(tts.spoken.isEmpty())
    }

    // endregion

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
    val volumes = mutableListOf<Float>()
    var stopCount = 0

    override fun speak(text: String, volume: Float) {
        spoken += text
        volumes += volume
    }

    override fun stop() {
        stopCount++
    }
}

private class FakeActionExecutor : ActionExecutor {
    var flashlightResult = ActionResult.SUCCESS
    var volumeResult = ActionResult.SUCCESS
    var alarmResult = ActionResult.SUCCESS
    var callResult = ActionResult.SUCCESS
    val callCalls = mutableListOf<String>()
    var messageResult = ActionResult.NEEDS_USER_STEP
    val messageCalls = mutableListOf<Pair<String, String>>()
    val flashlightCalls = mutableListOf<Boolean>()
    val volumeCalls = mutableListOf<VolumeDirection>()
    val alarmCalls = mutableListOf<Triple<Int, Int, String>>()

    override fun setFlashlight(on: Boolean): ActionResult {
        flashlightCalls += on
        return flashlightResult
    }

    override fun changeVolume(direction: VolumeDirection): ActionResult {
        volumeCalls += direction
        return volumeResult
    }

    override fun setAlarm(hour: Int, minute: Int, label: String): ActionResult {
        alarmCalls += Triple(hour, minute, label)
        return alarmResult
    }

    override fun call(phone: String): ActionResult {
        callCalls += phone
        return callResult
    }

    override fun sendMessage(phone: String, text: String): ActionResult {
        messageCalls += phone to text
        return messageResult
    }
}

/** Frases simples que dejan ver qué decidió el ViewModel. */
private class FakeTexts : ResponseTexts {
    override fun todayHeader(date: LocalDate) = "encabezado $date"
    override fun currentTime(time: LocalTime) = "hora $time"
    override fun currentDate(date: LocalDate) = "fecha $date"
    override fun flashlight(on: Boolean) = "linterna $on"
    override fun flashlightNotSupported() = "sin linterna"
    override fun volume(direction: VolumeDirection) = "volumen $direction"
    override fun volumeAtLimit(direction: VolumeDirection) = "volumen al limite $direction"
    override fun actionFailed() = "fallo"
    override fun notUnderstood() = "no entendi"
    override fun comingSoon(intent: UserIntent) = "pronto $intent"
    override fun nothingToConfirm() = "nada que confirmar"
    override fun speechError(error: SpeechError) = "error $error"
    override fun confirmation(intent: UserIntent) = ConfirmationTexts("¿$intent?", "detalle", "¿$intent?", "si")
    override fun answerYesOrNo() = "di si o no"
    override fun cancelled() = "cancelado"
    override fun clarification(reason: ClarificationReason) = "aclara $reason"
    override fun clarificationSpoken(reason: ClarificationReason, options: List<String>) = "aclara $reason $options"
    override fun optionLabel(intent: UserIntent) = "opcion $intent"
    override fun chooseAnOption() = "elige"
    override fun askForSlot(slot: Slot) = "falta $slot"
    override fun rejected(reason: RejectReason) = "rechazo $reason"
    override fun alarmSet(intent: UserIntent.SetAlarm) = "alarma $intent"
    override fun alarmNotSupported() = "sin reloj"
    override fun alarmLabel() = "Withyou"
    override fun calling(name: String) = "llamando $name"
    override fun dialerOpened(name: String) = "telefono abierto $name"
    override fun callNotSupported() = "sin llamadas"
    override fun contactNotFound(name: String) = "no encontre $name"
    override fun noContacts() = "sin contactos"
    override fun messageReady(name: String) = "mensaje listo $name"
    override fun messageNotSupported() = "sin mensajes"
}
