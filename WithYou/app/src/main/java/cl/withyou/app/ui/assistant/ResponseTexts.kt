package cl.withyou.app.ui.assistant

import cl.withyou.app.domain.model.ClarificationReason
import cl.withyou.app.domain.model.RejectReason
import cl.withyou.app.domain.model.Slot
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
    fun todayHeader(date: LocalDate): String
    fun currentTime(time: LocalTime): String
    fun currentDate(date: LocalDate): String
    fun flashlight(on: Boolean): String
    fun flashlightNotSupported(): String
    fun volume(direction: VolumeDirection): String
    fun volumeAtLimit(direction: VolumeDirection): String
    fun actionFailed(): String
    fun notUnderstood(): String
    fun comingSoon(intent: UserIntent): String
    fun nothingToConfirm(): String
    fun speechError(error: SpeechError): String

    /** Pantalla Confirmar (CU-08). */
    fun confirmation(intent: UserIntent): ConfirmationTexts
    fun answerYesOrNo(): String
    fun cancelled(): String

    /** Pantalla Aclarar (CU-06). */
    fun clarification(reason: ClarificationReason): String
    fun clarificationSpoken(reason: ClarificationReason, options: List<String>): String
    fun optionLabel(intent: UserIntent): String
    fun chooseAnOption(): String

    /** Pregunta por un dato que falta ("¿A qué hora?"). */
    fun askForSlot(slot: Slot): String

    fun rejected(reason: RejectReason): String

    /** Alarmas (RF-05). */
    fun alarmSet(intent: UserIntent.SetAlarm): String
    fun alarmNotSupported(): String
    fun alarmLabel(): String

    /** Llamadas (RF-06). */
    fun calling(name: String): String
    fun dialerOpened(name: String): String
    fun callNotSupported(): String
    fun contactNotFound(name: String): String
    fun noContacts(): String

    /** Mensajes (RF-07). */
    fun messageReady(name: String): String
    fun messageNotSupported(): String
}
