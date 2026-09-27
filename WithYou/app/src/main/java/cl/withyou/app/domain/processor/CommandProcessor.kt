package cl.withyou.app.domain.processor

import cl.withyou.app.domain.model.AlarmDay
import cl.withyou.app.domain.model.ClarificationReason
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.domain.model.ContactTarget
import cl.withyou.app.domain.model.Decision
import cl.withyou.app.domain.model.ParseResult
import cl.withyou.app.domain.model.RejectReason
import cl.withyou.app.domain.model.Slot
import cl.withyou.app.domain.model.TimeSpec
import cl.withyou.app.domain.model.UserIntent
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Decide qué hacer con lo interpretado: ejecutar, confirmar, aclarar, pedir un dato o rechazar.
 * Nunca falla en silencio (RNF-07): todo camino termina en una [Decision] que la UI puede mostrar.
 *
 * @param now reloj inyectable para poder probar las alarmas relativas y el día.
 */
class CommandProcessor(
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {

    fun process(result: ParseResult): Decision {
        val best = result.best
        if (best.intent == UserIntent.Unknown) {
            return Decision.AskClarification(ClarificationReason.NOT_UNDERSTOOD)
        }

        val second = result.candidates.getOrNull(1)
        val tooClose = second != null &&
            best.confidence - second.confidence < AMBIGUITY_MARGIN &&
            best.intent::class != second.intent::class
        if (best.confidence < HIGH_CONFIDENCE || tooClose) {
            val options = result.candidates
                .map { it.intent }
                .filter { it != UserIntent.Unknown && it != UserIntent.Confirm && it != UserIntent.Deny }
                .take(MAX_OPTIONS)
            val reason = if (options.isEmpty()) ClarificationReason.NOT_UNDERSTOOD else ClarificationReason.LOW_CONFIDENCE
            return Decision.AskClarification(reason, options)
        }
        return decide(best.intent)
    }

    /** Decide sobre una intención concreta, p. ej. la opción que el usuario eligió en una aclaración. */
    fun decide(intent: UserIntent): Decision = when (intent) {
        UserIntent.Confirm -> Decision.Reply(affirmative = true)
        UserIntent.Deny -> Decision.Reply(affirmative = false)
        UserIntent.Unknown -> Decision.AskClarification(ClarificationReason.NOT_UNDERSTOOD)
        UserIntent.AskTime,
        UserIntent.AskDate,
        is UserIntent.Flashlight,
        is UserIntent.Volume,
        -> Decision.Execute(intent)
        is UserIntent.SetAlarm -> decideAlarm(intent)
        is UserIntent.Call -> withContact(intent, intent.target, { UserIntent.Call(it) }) { contact ->
            Decision.AskConfirmation(UserIntent.Call(ContactTarget.Resolved(contact)))
        }
        is UserIntent.SendMessage ->
            withContact(intent, intent.target, { intent.copy(target = it) }) { contact ->
                decideMessage(intent.copy(target = ContactTarget.Resolved(contact)))
            }
    }

    private fun decideAlarm(intent: UserIntent.SetAlarm): Decision = when (val time = intent.time) {
        is TimeSpec.MissingHour -> Decision.AskForSlot(intent, Slot.TIME)
        is TimeSpec.MissingPeriod -> {
            val morning = time.hour % 12
            Decision.AskClarification(
                ClarificationReason.AMBIGUOUS_PERIOD,
                listOf(
                    intent.copy(time = TimeSpec.Exact(if (time.hour == 12) 12 else morning, time.minute)),
                    intent.copy(time = TimeSpec.Exact(if (time.hour == 12) 0 else morning + 12, time.minute)),
                ),
            )
        }
        is TimeSpec.InMinutes -> {
            if (time.minutes !in 1..MAX_RELATIVE_MINUTES) {
                Decision.Reject(RejectReason.INVALID_TIME, intent)
            } else {
                val current = now()
                val target = current.plusMinutes(time.minutes.toLong())
                val day = if (target.toLocalDate() == current.toLocalDate()) AlarmDay.TODAY else AlarmDay.TOMORROW
                Decision.AskConfirmation(UserIntent.SetAlarm(TimeSpec.Exact(target.hour, target.minute), day))
            }
        }
        is TimeSpec.Exact -> decideExactAlarm(intent, time)
    }

    private fun decideExactAlarm(intent: UserIntent.SetAlarm, time: TimeSpec.Exact): Decision {
        if (time.hour !in 0..23 || time.minute !in 0..59) {
            return Decision.Reject(RejectReason.INVALID_TIME, intent)
        }
        val alarmTime = LocalTime.of(time.hour, time.minute)
        val isLaterToday = alarmTime.isAfter(now().toLocalTime())
        val day = when (intent.day) {
            AlarmDay.UNSPECIFIED -> if (isLaterToday) AlarmDay.TODAY else AlarmDay.TOMORROW
            AlarmDay.TODAY -> if (isLaterToday) AlarmDay.TODAY else return Decision.Reject(RejectReason.TIME_ALREADY_PASSED, intent)
            AlarmDay.TOMORROW -> AlarmDay.TOMORROW
        }
        return Decision.AskConfirmation(intent.copy(day = day))
    }

    private fun decideMessage(intent: UserIntent.SendMessage): Decision {
        val text = intent.text?.replace(WHITESPACE, " ")?.trim()
        return when {
            text.isNullOrEmpty() -> Decision.AskForSlot(intent.copy(text = null), Slot.MESSAGE_TEXT)
            text.length > MAX_MESSAGE_LENGTH -> Decision.Reject(RejectReason.MESSAGE_TOO_LONG, intent)
            else -> Decision.AskConfirmation(intent.copy(text = text))
        }
    }

    /** Resuelve el contacto y valida su número antes de seguir con la acción. */
    private inline fun withContact(
        intent: UserIntent,
        target: ContactTarget,
        rebuild: (ContactTarget) -> UserIntent,
        onResolved: (Contact) -> Decision,
    ): Decision = when (target) {
        is ContactTarget.Resolved -> {
            val phone = sanitizePhone(target.contact.phone)
            if (phone == null) {
                Decision.Reject(RejectReason.INVALID_PHONE, intent)
            } else {
                onResolved(target.contact.copy(phone = phone))
            }
        }
        is ContactTarget.Ambiguous -> Decision.AskClarification(
            ClarificationReason.AMBIGUOUS_CONTACT,
            target.options.map { rebuild(ContactTarget.Resolved(it)) },
        )
        is ContactTarget.NotFound ->
            if (target.spokenName == null) {
                Decision.AskForSlot(intent, Slot.CONTACT)
            } else {
                Decision.AskClarification(
                    ClarificationReason.CONTACT_NOT_FOUND,
                    target.suggestions.map { rebuild(ContactTarget.Resolved(it)) },
                )
            }
    }

    /** Deja solo dígitos (y el "+" inicial). Devuelve null si el número no es válido. */
    internal fun sanitizePhone(raw: String): String? {
        val trimmed = raw.trim()
        if (!PHONE_CHARS.matches(trimmed)) return null
        val digits = trimmed.filter(Char::isDigit)
        if (digits.length !in 8..15) return null
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    companion object {
        const val HIGH_CONFIDENCE = 0.8
        const val AMBIGUITY_MARGIN = 0.1
        const val MAX_OPTIONS = 3
        const val MAX_MESSAGE_LENGTH = 480
        const val MAX_RELATIVE_MINUTES = 24 * 60

        private val PHONE_CHARS = Regex("\\+?[0-9 ()-]+")
        private val WHITESPACE = Regex("\\s+")
    }
}
