package cl.withyou.app.di

import android.content.Context
import cl.withyou.app.domain.parser.IntentParser
import cl.withyou.app.domain.processor.CommandProcessor
import cl.withyou.app.services.ActionExecutor
import cl.withyou.app.services.AndroidActionExecutor
import cl.withyou.app.services.AndroidSpeechService
import cl.withyou.app.services.AndroidTtsService
import cl.withyou.app.services.SpeechService
import cl.withyou.app.services.TtsService

/**
 * Inyección de dependencias manual: crea una sola vez los objetos compartidos de la app.
 * Los servicios se crean al primer uso y viven mientras viva la app.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val intentParser: IntentParser by lazy { IntentParser() }

    val commandProcessor: CommandProcessor by lazy { CommandProcessor() }

    val speechService: SpeechService by lazy { AndroidSpeechService(appContext) }

    val ttsService: TtsService by lazy { AndroidTtsService(appContext) }

    val actionExecutor: ActionExecutor by lazy { AndroidActionExecutor(appContext) }
}
