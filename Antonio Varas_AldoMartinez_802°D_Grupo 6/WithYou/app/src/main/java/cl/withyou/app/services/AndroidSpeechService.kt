package cl.withyou.app.services

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Implementación con [SpeechRecognizer]. Usa el contexto de Application, nunca el de una Activity. */
class AndroidSpeechService(context: Context) : SpeechService {

    private val appContext = context.applicationContext
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 16)
    override val events: Flow<SpeechEvent> = _events.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Se intenta primero el reconocimiento sin conexión (más rápido y privado). Los equipos
     * Android Go no tienen motor offline y fallan al instante con un "error de red": en ese
     * caso se reintenta en línea y no se vuelve a pedir el modo offline.
     */
    private var preferOffline = !appContext.getSystemService(ActivityManager::class.java).isLowRamDevice
    private var speechStarted = false

    override fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _events.tryEmit(SpeechEvent.Error(SpeechError.UNAVAILABLE))
            return
        }
        speechStarted = false
        (recognizer ?: createRecognizer()).startListening(buildIntent())
    }

    private fun createRecognizer(): SpeechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }

    /**
     * Reintenta en línea con un reconocedor nuevo y una pequeña pausa: si se reutiliza el
     * anterior, el cierre de la sesión fallida cancela la nueva y no llega ningún resultado.
     */
    private fun retryOnline() {
        preferOffline = false
        recognizer?.destroy()
        recognizer = null
        mainHandler.postDelayed({
            speechStarted = false
            createRecognizer().startListening(buildIntent())
        }, RETRY_DELAY_MS)
    }

    override fun stopListening() {
        recognizer?.stopListening()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _events.tryEmit(SpeechEvent.Ready)
        }

        override fun onEndOfSpeech() {
            _events.tryEmit(SpeechEvent.EndOfSpeech)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.texts().firstOrNull()
            if (!text.isNullOrBlank()) _events.tryEmit(SpeechEvent.Partial(text))
        }

        override fun onResults(results: Bundle?) {
            _events.tryEmit(SpeechEvent.Results(results.texts()))
        }

        override fun onError(error: Int) {
            val reason = mapError(error)
            if (reason == SpeechError.NETWORK && preferOffline && !speechStarted) {
                Log.i(TAG, "Sin motor de voz offline (código $error); se reintenta en línea")
                retryOnline()
                return
            }
            _events.tryEmit(SpeechEvent.Error(reason))
        }

        override fun onBeginningOfSpeech() {
            speechStarted = true
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

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

    private fun mapError(error: Int): SpeechError = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> SpeechError.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.NO_SPEECH
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        -> SpeechError.NETWORK
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechError.BUSY
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.NO_PERMISSION
        ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> SpeechError.UNAVAILABLE
        else -> SpeechError.OTHER
    }

    private companion object {
        const val TAG = "WithYou.Speech"
        const val LANGUAGE = "es-CL"
        const val MAX_RESULTS = 3
        const val RETRY_DELAY_MS = 250L

        // Constantes de API 31; se copian para poder compilar con minSdk 26.
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
