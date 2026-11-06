package cl.withyou.app.di

import android.content.Context
import cl.withyou.app.data.local.AppDatabase
import cl.withyou.app.data.prefs.DataStorePreferencesRepository
import cl.withyou.app.data.prefs.PreferencesRepository
import cl.withyou.app.data.prefs.settingsDataStore
import cl.withyou.app.data.repository.ContactRepository
import cl.withyou.app.data.repository.RoomContactRepository
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

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    val contactRepository: ContactRepository by lazy { RoomContactRepository(database.contactDao()) }

    val preferencesRepository: PreferencesRepository by lazy {
        DataStorePreferencesRepository(appContext.settingsDataStore)
    }

    val intentParser: IntentParser by lazy { IntentParser() }

    val commandProcessor: CommandProcessor by lazy { CommandProcessor() }

    val speechService: SpeechService by lazy { AndroidSpeechService(appContext) }

    val ttsService: TtsService by lazy { AndroidTtsService(appContext) }

    val actionExecutor: ActionExecutor by lazy { AndroidActionExecutor(appContext) }
}
