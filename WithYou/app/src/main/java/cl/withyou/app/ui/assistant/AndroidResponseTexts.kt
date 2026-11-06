package cl.withyou.app.ui.assistant

import android.content.Context
import androidx.annotation.StringRes
import cl.withyou.app.R
import cl.withyou.app.domain.model.AlarmDay
import cl.withyou.app.domain.model.ClarificationReason
import cl.withyou.app.domain.model.ContactTarget
import cl.withyou.app.domain.model.RejectReason
import cl.withyou.app.domain.model.Slot
import cl.withyou.app.domain.model.TimeSpec
import cl.withyou.app.domain.model.UserIntent
import cl.withyou.app.domain.model.VolumeDirection
import cl.withyou.app.services.SpeechError
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Arma las frases desde strings.xml usando el contexto de Application. */
class AndroidResponseTexts(context: Context) : ResponseTexts {

    private val appContext = context.applicationContext
    private val locale = Locale.forLanguageTag("es-CL")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", locale)
    private val headerFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)

    override fun todayHeader(date: LocalDate): String =
        date.format(headerFormatter).replaceFirstChar { it.titlecase(locale) }

    /** "Son las 10 y 25 de la mañana", "Es la 1 y media de la tarde". */
    override fun currentTime(time: LocalTime): String {
        val hour12 = hour12(time.hour)
        val clock = when (time.minute) {
            0 -> string(R.string.time_oclock, hour12)
            15 -> string(R.string.time_quarter, hour12)
            30 -> string(R.string.time_half, hour12)
            else -> string(R.string.time_minutes, hour12, time.minute)
        }
        return string(if (hour12 == 1) R.string.time_is_one else R.string.time_is, clock, period(time.hour))
    }

    override fun currentDate(date: LocalDate): String =
        string(R.string.date_today, date.format(dateFormatter))

    override fun flashlight(on: Boolean): String =
        string(if (on) R.string.flashlight_on else R.string.flashlight_off)

    override fun flashlightNotSupported(): String = string(R.string.flashlight_not_supported)

    override fun volume(direction: VolumeDirection): String =
        string(if (direction == VolumeDirection.UP) R.string.volume_up else R.string.volume_down)

    override fun volumeAtLimit(direction: VolumeDirection): String =
        string(if (direction == VolumeDirection.UP) R.string.volume_at_max else R.string.volume_at_min)

    override fun actionFailed(): String = string(R.string.action_failed)

    override fun notUnderstood(): String = string(R.string.not_understood)

    override fun comingSoon(intent: UserIntent): String = string(R.string.coming_soon_intent, describe(intent))

    override fun nothingToConfirm(): String = string(R.string.nothing_to_confirm)

    override fun speechError(error: SpeechError): String = string(
        when (error) {
            SpeechError.NO_MATCH, SpeechError.NO_SPEECH -> R.string.speech_error_no_speech
            SpeechError.NETWORK -> R.string.speech_error_network
            SpeechError.BUSY -> R.string.speech_error_busy
            SpeechError.NO_PERMISSION -> R.string.speech_error_permission
            SpeechError.UNAVAILABLE -> R.string.speech_error_unavailable
            SpeechError.OTHER -> R.string.speech_error_other
        },
    )

    override fun confirmation(intent: UserIntent): ConfirmationTexts = when (intent) {
        is UserIntent.SetAlarm -> {
            val moment = alarmMoment(intent)
            ConfirmationTexts(
                title = string(R.string.confirm_alarm_title),
                detail = moment.replaceFirstChar { it.titlecase(locale) },
                question = string(R.string.confirm_alarm_question, moment),
                confirmLabel = string(R.string.confirm_alarm_button),
            )
        }
        is UserIntent.Call -> {
            val name = nameOf(intent.target).orEmpty()
            ConfirmationTexts(
                title = string(R.string.confirm_call_title, name),
                detail = (intent.target as? ContactTarget.Resolved)?.contact?.phone.orEmpty(),
                question = string(R.string.confirm_call_question, name),
                confirmLabel = string(R.string.confirm_call_button),
            )
        }
        is UserIntent.SendMessage -> {
            val name = nameOf(intent.target).orEmpty()
            val text = intent.text.orEmpty()
            ConfirmationTexts(
                title = string(R.string.confirm_message_title, name),
                detail = string(R.string.quoted, text),
                question = string(R.string.confirm_message_question, name, text),
                confirmLabel = string(R.string.confirm_message_button),
            )
        }
        else -> {
            val description = describe(intent)
            ConfirmationTexts(
                title = optionLabel(intent),
                detail = "",
                question = string(R.string.confirm_generic_question, description),
                confirmLabel = string(R.string.confirm_generic_button),
            )
        }
    }

    override fun answerYesOrNo(): String = string(R.string.answer_yes_or_no)

    override fun cancelled(): String = string(R.string.cancelled)

    override fun clarification(reason: ClarificationReason): String = string(
        when (reason) {
            ClarificationReason.AMBIGUOUS_PERIOD -> R.string.clarify_period
            ClarificationReason.AMBIGUOUS_CONTACT -> R.string.clarify_contact
            ClarificationReason.CONTACT_NOT_FOUND -> R.string.clarify_contact_not_found
            ClarificationReason.LOW_CONFIDENCE, ClarificationReason.NOT_UNDERSTOOD -> R.string.clarify_did_you_mean
        },
    )

    /** "¿Quisiste decir…? Uno: llamar a María. Dos: …" */
    override fun clarificationSpoken(reason: ClarificationReason, options: List<String>): String {
        val enumerated = options.mapIndexed { i, label -> string(R.string.clarify_spoken_option, i + 1, label) }
        return listOf(clarification(reason), *enumerated.toTypedArray(), string(R.string.clarify_spoken_hint))
            .joinToString(" ")
    }

    override fun optionLabel(intent: UserIntent): String = describe(intent).replaceFirstChar { it.titlecase(locale) }

    override fun chooseAnOption(): String = string(R.string.choose_an_option)

    override fun askForSlot(slot: Slot): String = string(
        when (slot) {
            Slot.TIME -> R.string.ask_slot_time
            Slot.CONTACT -> R.string.ask_slot_contact
            Slot.MESSAGE_TEXT -> R.string.ask_slot_message
        },
    )

    override fun rejected(reason: RejectReason): String = string(
        when (reason) {
            RejectReason.INVALID_TIME -> R.string.rejected_invalid_time
            RejectReason.TIME_ALREADY_PASSED -> R.string.rejected_time_passed
            RejectReason.INVALID_PHONE -> R.string.rejected_invalid_phone
            RejectReason.MESSAGE_TOO_LONG -> R.string.rejected_message_too_long
        },
    )

    override fun alarmSet(intent: UserIntent.SetAlarm): String = string(R.string.alarm_set, alarmMoment(intent))

    override fun alarmNotSupported(): String = string(R.string.alarm_not_supported)

    override fun alarmLabel(): String = string(R.string.app_name)

    override fun calling(name: String): String = string(R.string.calling, name)

    override fun dialerOpened(name: String): String = string(R.string.dialer_opened, name)

    override fun callNotSupported(): String = string(R.string.call_not_supported)

    override fun contactNotFound(name: String): String = string(R.string.contact_not_found, name)

    override fun noContacts(): String = string(R.string.no_contacts)

    override fun messageReady(name: String): String = string(R.string.message_ready, name)

    override fun messageNotSupported(): String = string(R.string.message_not_supported)

    /** Describe una intención en infinitivo: "llamar a María", "poner una alarma para mañana a las 8:00…". */
    private fun describe(intent: UserIntent): String = when (intent) {
        is UserIntent.SetAlarm ->
            if (intent.time is TimeSpec.Exact) string(R.string.intent_alarm_at, alarmMoment(intent)) else string(R.string.intent_alarm)
        is UserIntent.Call -> nameOf(intent.target)
            ?.let { string(R.string.intent_call_to, it) }
            ?: string(R.string.intent_call)
        is UserIntent.SendMessage -> nameOf(intent.target)
            ?.let { string(R.string.intent_message_to, it) }
            ?: string(R.string.intent_message)
        is UserIntent.Flashlight ->
            string(if (intent.turnOn) R.string.intent_flashlight_on else R.string.intent_flashlight_off)
        is UserIntent.Volume ->
            string(if (intent.direction == VolumeDirection.UP) R.string.intent_volume_up else R.string.intent_volume_down)
        UserIntent.AskTime -> string(R.string.intent_time)
        UserIntent.AskDate -> string(R.string.intent_date)
        UserIntent.Confirm, UserIntent.Deny, UserIntent.Unknown -> string(R.string.intent_unknown)
    }

    /**
     * Cuándo suena la alarma, en palabras: "mañana a las 8:00 de la mañana", "hoy a la 1:30 de la tarde".
     * Sin día ("a las 8:00 de la noche") cuando aún no se sabe.
     */
    private fun alarmMoment(intent: UserIntent.SetAlarm): String {
        val time = intent.time as? TimeSpec.Exact ?: return string(R.string.intent_alarm)
        val hour12 = hour12(time.hour)
        val clock = String.format(Locale.ROOT, "%d:%02d", hour12, time.minute)
        val at = string(if (hour12 == 1) R.string.alarm_at_one else R.string.alarm_at, clock, period(time.hour))
        return when (intent.day) {
            AlarmDay.TODAY -> string(R.string.alarm_moment, string(R.string.day_today), at)
            AlarmDay.TOMORROW -> string(R.string.alarm_moment, string(R.string.day_tomorrow), at)
            AlarmDay.UNSPECIFIED -> at
        }
    }

    private fun hour12(hour: Int): Int = if (hour % 12 == 0) 12 else hour % 12

    private fun period(hour: Int): String = string(
        when (hour) {
            in 0..5 -> R.string.period_early_morning
            in 6..11 -> R.string.period_morning
            12 -> R.string.period_noon
            in 13..19 -> R.string.period_afternoon
            else -> R.string.period_night
        },
    )

    private fun nameOf(target: ContactTarget): String? = when (target) {
        is ContactTarget.Resolved -> target.contact.name
        is ContactTarget.Ambiguous -> target.spokenName
        is ContactTarget.NotFound -> target.spokenName
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)
}
