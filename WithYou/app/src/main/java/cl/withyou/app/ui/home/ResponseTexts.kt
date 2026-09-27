package cl.withyou.app.ui.home

import cl.withyou.app.domain.model.UserIntent
import cl.withyou.app.domain.model.VolumeDirection
import cl.withyou.app.services.SpeechError
import java.time.LocalDate
import java.time.LocalTime

/**
 * Frases que la app muestra y dice. Es una interfaz para que el ViewModel no dependa
 * de los recursos de Android y se pueda probar con JUnit.
 */
interface ResponseTexts {
    fun currentTime(time: LocalTime): String
    fun currentDate(date: LocalDate): String
    fun flashlight(on: Boolean): String
    fun flashlightNotSupported(): String
    fun volume(direction: VolumeDirection): String
    fun volumeAtLimit(direction: VolumeDirection): String
    fun actionFailed(): String
    fun notUnderstood(): String
    fun didYouMean(options: List<UserIntent>): String
    fun comingSoon(intent: UserIntent): String
    fun nothingToConfirm(): String
    fun speechError(error: SpeechError): String
}
