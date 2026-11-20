package cl.withyou.app.services

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Implementación con [SpeechRecognizer]. Usa el contexto de Application, nunca el de una Activity.
 *
 * Cada teléfono trae reconocedores distintos, así que si uno falla **antes de que el usuario
 * hable**, se prueba el siguiente de esta cadena:
 * 1. El reconocedor predeterminado sin conexión (más rápido y privado).
 * 2. El mismo, en línea. Android Go no tiene motor sin conexión ("error de red"), y otros
 *    teléfonos no tienen el español descargado ("idioma no disponible").
 * 3. Los demás reconocedores instalados, primero los de Google. En algunos Samsung recientes el
 *    predeterminado (Android System Intelligence) funciona solo en el teléfono y puede no tener
 *    español de Chile.
 *
 * Se recuerda el reconocedor que funcionó, para no repetir los intentos fallidos.
 */
class AndroidSpeechService(context: Context) : SpeechService {

    private val appContext = context.applicationContext
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events: Flow<SpeechEvent> = _events.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** null = reconocedor predeterminado; después, los demás instalados. */
    private val candidates: List<ComponentName?> by lazy { listOf<ComponentName?>(null) + otherRecognizers() }
    private var candidateIndex = 0

    /** Último reconocedor que entregó resultados: por él se parte la próxima vez. */
    private var workingIndex = 0

    private var preferOffline = !appContext.getSystemService(ActivityManager::class.java).isLowRamDevice
    private var speechStarted = false

    override fun startListening() {
        val defaultAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        if (!defaultAvailable && candidates.size == 1) {
            Log.w(TAG, "No hay ningún reconocedor de voz instalado")
            _events.tryEmit(SpeechEvent.Error(SpeechError.UNAVAILABLE))
            return
        }
        if (!defaultAvailable && candidateIndex == 0) switchTo(1)
        speechStarted = false
        (recognizer ?: createRecognizer()).startListening(buildIntent())
    }

    override fun stopListening() {
        recognizer?.stopListening()
    }

    private fun createRecognizer(): SpeechRecognizer {
        val component = candidates[candidateIndex]
        val created = if (component == null) {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext, component)
        }
        return created.also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
    }

    private fun switchTo(index: Int) {
        recognizer?.destroy()
        recognizer = null
        candidateIndex = index
    }

    /**
     * Vuelve a intentar con un reconocedor nuevo y una pequeña pausa: si se reutiliza el
     * anterior, el cierre de la sesión fallida cancela la nueva y no llega ningún resultado.
     */
    private fun retry(nextIndex: Int) {
        switchTo(nextIndex)
        mainHandler.postDelayed({
            speechStarted = false
            createRecognizer().startListening(buildIntent())
        }, RETRY_DELAY_MS)
    }

    /** Reconocedores instalados distintos del predeterminado, primero los de Google. */
    private fun otherRecognizers(): List<ComponentName> {
        val installed = appContext.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
        val default = Settings.Secure.getString(appContext.contentResolver, DEFAULT_RECOGNIZER_SETTING)
            ?.let(ComponentName::unflattenFromString)
        val others = installed.filter { it != default }.sortedByDescending { it.packageName in GOOGLE_PACKAGES }
        Log.i(TAG, "Reconocedor predeterminado: $default; alternativas: $others")
        return others
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _events.tryEmit(SpeechEvent.Ready)
        }

        override fun onBeginningOfSpeech() {
            speechStarted = true
        }

        override fun onEndOfSpeech() {
            _events.tryEmit(SpeechEvent.EndOfSpeech)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.texts().firstOrNull()
            if (!text.isNullOrBlank()) _events.tryEmit(SpeechEvent.Partial(text))
        }

        override fun onResults(results: Bundle?) {
            workingIndex = candidateIndex
            _events.tryEmit(SpeechEvent.Results(results.texts()))
        }

        override fun onError(error: Int) {
            val reason = mapError(error)
            val setupFailed = !speechStarted &&
                (reason == SpeechError.NETWORK || reason == SpeechError.UNAVAILABLE || error.isLanguageError())
            when {
                setupFailed && preferOffline -> {
                    Log.i(TAG, "El reconocimiento sin conexión falló (código $error); se reintenta en línea")
                    preferOffline = false
                    retry(candidateIndex)
                }
                setupFailed && candidateIndex < candidates.lastIndex -> {
                    Log.i(TAG, "${describe(candidateIndex)} falló (código $error); se prueba ${describe(candidateIndex + 1)}")
                    retry(candidateIndex + 1)
                }
                else -> {
                    Log.w(TAG, "Error del reconocedor de voz ${describe(candidateIndex)}: código $error ($reason)")
                    // Si fallaron todos, la próxima vez se parte por el último que funcionó.
                    if (setupFailed) switchTo(workingIndex)
                    _events.tryEmit(SpeechEvent.Error(if (error.isLanguageError()) SpeechError.LANGUAGE else reason))
                }
            }
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun describe(index: Int): String = candidates.getOrNull(index)?.flattenToShortString() ?: "el predeterminado"

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
    }

    private fun Bundle?.texts(): List<String> =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()

    private fun Int.isLanguageError() = this == ERROR_LANGUAGE_NOT_SUPPORTED || this == ERROR_LANGUAGE_UNAVAILABLE

    private fun mapError(error: Int): SpeechError = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> SpeechError.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.NO_SPEECH
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        -> SpeechError.NETWORK
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechError.BUSY
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.NO_PERMISSION
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> SpeechError.LANGUAGE
        else -> SpeechError.OTHER
    }

    private companion object {
        const val TAG = "WithYou.Speech"
        const val LANGUAGE = "es-CL"
        const val MAX_RESULTS = 3
        const val RETRY_DELAY_MS = 250L

        /** Ajuste del sistema con el reconocedor predeterminado ("paquete/servicio"). */
        const val DEFAULT_RECOGNIZER_SETTING = "voice_recognition_service"

        /** Reconocedores de Google: la app de Google y "Servicios de voz" de Android Go. */
        val GOOGLE_PACKAGES = setOf("com.google.android.googlequicksearchbox", "com.google.android.apps.speechservices")

        // Constantes de API 31; se copian para poder compilar con minSdk 26.
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
