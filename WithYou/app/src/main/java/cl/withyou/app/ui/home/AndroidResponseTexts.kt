package cl.withyou.app.ui.home

import android.content.Context
import androidx.annotation.StringRes
import cl.withyou.app.R
import cl.withyou.app.domain.model.ContactTarget
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

    /** "Son las 10 y 25 de la mañana", "Es la 1 y media de la tarde". */
    override fun currentTime(time: LocalTime): String {
        val hour12 = if (time.hour % 12 == 0) 12 else time.hour % 12
        val clock = when (time.minute) {
            0 -> string(R.string.time_oclock, hour12)
            15 -> string(R.string.time_quarter, hour12)
            30 -> string(R.string.time_half, hour12)
            else -> string(R.string.time_minutes, hour12, time.minute)
        }
        val period = string(
            when (time.hour) {
                in 0..5 -> R.string.period_early_morning
                in 6..11 -> R.string.period_morning
                12 -> R.string.period_noon
                in 13..19 -> R.string.period_afternoon
                else -> R.string.period_night
            },
        )
        return string(if (hour12 == 1) R.string.time_is_one else R.string.time_is, clock, period)
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

    override fun didYouMean(options: List<UserIntent>): String {
        if (options.isEmpty()) return notUnderstood()
        val joined = options.map(::describe).distinct().joinToString(string(R.string.options_separator))
        return string(R.string.did_you_mean, joined)
    }

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

    /** Describe una intención en infinitivo: "llamar a María", "poner una alarma a las 8:00". */
    private fun describe(intent: UserIntent): String = when (intent) {
        is UserIntent.SetAlarm -> (intent.time as? TimeSpec.Exact)
            ?.let { string(R.string.intent_alarm_at, String.format(Locale.ROOT, "%d:%02d", it.hour, it.minute)) }
            ?: string(R.string.intent_alarm)
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

    private fun nameOf(target: ContactTarget): String? = when (target) {
        is ContactTarget.Resolved -> target.contact.name
        is ContactTarget.Ambiguous -> target.spokenName
        is ContactTarget.NotFound -> target.spokenName
    }

    private fun string(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)
}
