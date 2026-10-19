package cl.withyou.app.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Implementación con [TextToSpeech] en español de Chile, con respaldo a otras variantes de español.
 * El motor tarda en iniciar: lo que se pida decir antes queda pendiente y se dice al terminar.
 */
class AndroidTtsService(context: Context) : TtsService, TextToSpeech.OnInitListener {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var ready = false
    private var pending: String? = null
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "No se pudo iniciar el motor de voz (estado $status)")
            return
        }
        val locale = PREFERRED_LOCALES.firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        if (locale != null) tts.language = locale else Log.w(TAG, "El motor de voz no tiene español instalado")
        tts.setSpeechRate(SPEECH_RATE)
        tts.setOnUtteranceProgressListener(progressListener)
        ready = true
        pending?.let(::speak)
        pending = null
    }

    override fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun stop() {
        pending = null
        if (ready) tts.stop()
        _isSpeaking.value = false
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _isSpeaking.value = true
        }

        override fun onDone(utteranceId: String?) {
            _isSpeaking.value = false
        }

        @Deprecated("Requerido por la clase abstracta")
        override fun onError(utteranceId: String?) {
            _isSpeaking.value = false
        }
    }

    private companion object {
        const val TAG = "WithYou.Tts"
        const val UTTERANCE_ID = "withyou-response"

        /** Un poco más lento que lo normal para que se entienda mejor. */
        const val SPEECH_RATE = 0.9f

        val PREFERRED_LOCALES = listOf("es-CL", "es-US", "es-419", "es-ES", "es").map(Locale::forLanguageTag)
    }
}
