package cl.withyou.app.domain.processor

import cl.withyou.app.domain.model.AlarmDay
import cl.withyou.app.domain.model.Candidate
import cl.withyou.app.domain.model.ClarificationReason
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.domain.model.ContactTarget
import cl.withyou.app.domain.model.Decision
import cl.withyou.app.domain.model.ParseResult
import cl.withyou.app.domain.model.RejectReason
import cl.withyou.app.domain.model.Slot
import cl.withyou.app.domain.model.TimeSpec
import cl.withyou.app.domain.model.UserIntent
import cl.withyou.app.domain.model.VolumeDirection
import cl.withyou.app.domain.parser.IntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class CommandProcessorTest {

    /** Reloj fijo: domingo 27 de septiembre de 2026, 10:00. */
    private val processor = CommandProcessor { LocalDateTime.of(2026, 9, 27, 10, 0) }

    private val maria = Contact(1, "María González", "+56 9 1111 1111")
    private val mariaPerez = Contact(2, "María Pérez", "+56922222222")

    private fun resultOf(vararg candidates: Pair<UserIntent, Double>) =
        ParseResult("", candidates.map { Candidate(it.first, it.second) })

    @Test
    fun `acciones no criticas se ejecutan directo`() {
        listOf(
            UserIntent.AskTime,
            UserIntent.AskDate,
            UserIntent.Flashlight(true),
            UserIntent.Volume(VolumeDirection.UP),
        ).forEach { intent ->
            assertEquals(Decision.Execute(intent), processor.process(resultOf(intent to 0.95)))
        }
    }

    @Test
    fun `si y no se devuelven como respuesta`() {
        assertEquals(Decision.Reply(true), processor.process(resultOf(UserIntent.Confirm to 0.95)))
        assertEquals(Decision.Reply(false), processor.process(resultOf(UserIntent.Deny to 0.95)))
    }

    @Test
    fun `frase no entendida pide aclaracion`() {
        assertEquals(
            Decision.AskClarification(ClarificationReason.NOT_UNDERSTOOD),
            processor.process(resultOf(UserIntent.Unknown to 0.0)),
        )
    }

    @Test
    fun `baja confianza ofrece los candidatos`() {
        val call = UserIntent.Call(ContactTarget.Resolved(maria))
        val message = UserIntent.SendMessage(ContactTarget.Resolved(maria))
        assertEquals(
            Decision.AskClarification(ClarificationReason.LOW_CONFIDENCE, listOf(call, message)),
            processor.process(resultOf(call to 0.5, message to 0.45)),
        )
    }

    @Test
    fun `dos intenciones distintas casi empatadas piden aclaracion`() {
        val call = UserIntent.Call(ContactTarget.Resolved(maria))
        val message = UserIntent.SendMessage(ContactTarget.Resolved(maria))
        val decision = processor.process(resultOf(call to 0.92, message to 0.9))
        assertEquals(Decision.AskClarification(ClarificationReason.LOW_CONFIDENCE, listOf(call, message)), decision)
    }

    @Test
    fun `alarma exacta pide confirmacion y calcula el dia`() {
        assertEquals(
            Decision.AskConfirmation(UserIntent.SetAlarm(TimeSpec.Exact(20, 0), AlarmDay.TODAY)),
            processor.decide(UserIntent.SetAlarm(TimeSpec.Exact(20, 0))),
        )
        assertEquals(
            Decision.AskConfirmation(UserIntent.SetAlarm(TimeSpec.Exact(8, 0), AlarmDay.TOMORROW)),
            processor.decide(UserIntent.SetAlarm(TimeSpec.Exact(8, 0))),
        )
    }

    @Test
    fun `alarma para hoy a una hora que ya paso se rechaza`() {
        val intent = UserIntent.SetAlarm(TimeSpec.Exact(7, 0), AlarmDay.TODAY)
        assertEquals(Decision.Reject(RejectReason.TIME_ALREADY_PASSED, intent), processor.decide(intent))
    }

    @Test
    fun `alarma sin mañana o tarde ofrece ambas opciones`() {
        assertEquals(
            Decision.AskClarification(
                ClarificationReason.AMBIGUOUS_PERIOD,
                listOf(
                    UserIntent.SetAlarm(TimeSpec.Exact(8, 30)),
                    UserIntent.SetAlarm(TimeSpec.Exact(20, 30)),
                ),
            ),
            processor.decide(UserIntent.SetAlarm(TimeSpec.MissingPeriod(8, 30))),
        )
        assertEquals(
            Decision.AskClarification(
                ClarificationReason.AMBIGUOUS_PERIOD,
                listOf(UserIntent.SetAlarm(TimeSpec.Exact(12, 0)), UserIntent.SetAlarm(TimeSpec.Exact(0, 0))),
            ),
            processor.decide(UserIntent.SetAlarm(TimeSpec.MissingPeriod(12, 0))),
        )
    }

    @Test
    fun `alarma sin hora pide la hora`() {
        val intent = UserIntent.SetAlarm(TimeSpec.MissingHour(null))
        assertEquals(Decision.AskForSlot(intent, Slot.TIME), processor.decide(intent))
    }

    @Test
    fun `alarma relativa se convierte en hora exacta`() {
        assertEquals(
            Decision.AskConfirmation(UserIntent.SetAlarm(TimeSpec.Exact(10, 30), AlarmDay.TODAY)),
            processor.decide(UserIntent.SetAlarm(TimeSpec.InMinutes(30))),
        )
        assertEquals(
            Decision.AskConfirmation(UserIntent.SetAlarm(TimeSpec.Exact(6, 0), AlarmDay.TOMORROW)),
            processor.decide(UserIntent.SetAlarm(TimeSpec.InMinutes(20 * 60))),
        )
    }

    @Test
    fun `hora invalida se rechaza`() {
        val intent = UserIntent.SetAlarm(TimeSpec.Exact(25, 0))
        assertEquals(Decision.Reject(RejectReason.INVALID_TIME, intent), processor.decide(intent))
    }

    @Test
    fun `llamada pide confirmacion con el numero saneado`() {
        assertEquals(
            Decision.AskConfirmation(UserIntent.Call(ContactTarget.Resolved(maria.copy(phone = "+56911111111")))),
            processor.decide(UserIntent.Call(ContactTarget.Resolved(maria))),
        )
    }

    @Test
    fun `numero invalido se rechaza`() {
        val intent = UserIntent.Call(ContactTarget.Resolved(maria.copy(phone = "llamar*123#")))
        assertEquals(Decision.Reject(RejectReason.INVALID_PHONE, intent), processor.decide(intent))
    }

    @Test
    fun `contacto ambiguo pide elegir`() {
        val intent = UserIntent.Call(ContactTarget.Ambiguous("María", listOf(maria, mariaPerez)))
        assertEquals(
            Decision.AskClarification(
                ClarificationReason.AMBIGUOUS_CONTACT,
                listOf(
                    UserIntent.Call(ContactTarget.Resolved(maria)),
                    UserIntent.Call(ContactTarget.Resolved(mariaPerez)),
                ),
            ),
            processor.decide(intent),
        )
    }

    @Test
    fun `contacto no dicho pide el contacto`() {
        val intent = UserIntent.Call(ContactTarget.NotFound(null))
        assertEquals(Decision.AskForSlot(intent, Slot.CONTACT), processor.decide(intent))
    }

    @Test
    fun `contacto no encontrado lo informa con sugerencias`() {
        assertEquals(
            Decision.AskClarification(ClarificationReason.CONTACT_NOT_FOUND, emptyList()),
            processor.decide(UserIntent.Call(ContactTarget.NotFound("Roberto"))),
        )
    }

    @Test
    fun `mensaje sin texto pide dictarlo`() {
        val decision = processor.decide(UserIntent.SendMessage(ContactTarget.Resolved(maria)))
        decision as Decision.AskForSlot
        assertEquals(Slot.MESSAGE_TEXT, decision.slot)
        assertNull((decision.intent as UserIntent.SendMessage).text)
    }

    @Test
    fun `mensaje con texto pide confirmacion`() {
        val decision = processor.decide(UserIntent.SendMessage(ContactTarget.Resolved(maria), "  Llego   tarde "))
        assertEquals(
            Decision.AskConfirmation(
                UserIntent.SendMessage(ContactTarget.Resolved(maria.copy(phone = "+56911111111")), "Llego tarde"),
            ),
            decision,
        )
    }

    @Test
    fun `mensaje demasiado largo se rechaza`() {
        val intent = UserIntent.SendMessage(ContactTarget.Resolved(maria), "a".repeat(481))
        assertEquals(RejectReason.MESSAGE_TOO_LONG, (processor.decide(intent) as Decision.Reject).reason)
    }

    @Test
    fun `saneamiento de telefonos`() {
        assertEquals("+56912345678", processor.sanitizePhone("+56 9 1234 5678"))
        assertEquals("912345678", processor.sanitizePhone("9-1234-5678"))
        assertNull(processor.sanitizePhone("1234"))
        assertNull(processor.sanitizePhone("*123#"))
        assertNull(processor.sanitizePhone("tel:912345678"))
    }

    @Test
    fun `de punta a punta - frase hablada a decision`() {
        val parser = IntentParser()
        val contacts = listOf(maria)
        val cases = listOf(
            "pon una alarma para mañana a las ocho de la mañana" to
                Decision.AskConfirmation(UserIntent.SetAlarm(TimeSpec.Exact(8, 0), AlarmDay.TOMORROW)),
            "enciende la linterna" to Decision.Execute(UserIntent.Flashlight(true)),
            "¿qué hora es?" to Decision.Execute(UserIntent.AskTime),
            "llama a María" to
                Decision.AskConfirmation(UserIntent.Call(ContactTarget.Resolved(maria.copy(phone = "+56911111111")))),
            "sí" to Decision.Reply(true),
            "bla bla bla" to Decision.AskClarification(ClarificationReason.NOT_UNDERSTOOD),
        )
        cases.forEach { (phrase, expected) ->
            assertEquals("Frase: \"$phrase\"", expected, processor.process(parser.parse(phrase, contacts)))
        }
    }
}
