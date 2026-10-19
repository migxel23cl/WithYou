package cl.withyou.app.domain.parser

import cl.withyou.app.domain.model.AlarmDay
import cl.withyou.app.domain.model.DayPeriod
import cl.withyou.app.domain.model.TimeSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeExtractorTest {

    private val extractor = TimeExtractor()

    @Test
    fun `extrae la hora de frases habladas`() {
        val cases = listOf(
            "a las 8" to TimeSpec.MissingPeriod(8, 0),
            "a las ocho" to TimeSpec.MissingPeriod(8, 0),
            "a las 8:30" to TimeSpec.MissingPeriod(8, 30),
            "a las 20:15" to TimeSpec.Exact(20, 15),
            "a las 0:30" to TimeSpec.Exact(0, 30),
            "a las ocho de la mañana" to TimeSpec.Exact(8, 0),
            "a las ocho y media" to TimeSpec.MissingPeriod(8, 30),
            "a las ocho y media de la noche" to TimeSpec.Exact(20, 30),
            "a las siete y cuarto de la tarde" to TimeSpec.Exact(19, 15),
            "a las siete y veinte" to TimeSpec.MissingPeriod(7, 20),
            "a las seis y treinta y cinco de la mañana" to TimeSpec.Exact(6, 35),
            "a las nueve menos cuarto de la mañana" to TimeSpec.Exact(8, 45),
            "un cuarto para las nueve" to TimeSpec.MissingPeriod(8, 45),
            "diez para las ocho de la noche" to TimeSpec.Exact(19, 50),
            "cuarto para la una" to TimeSpec.MissingPeriod(12, 45),
            "a la una de la tarde" to TimeSpec.Exact(13, 0),
            "a las doce de la noche" to TimeSpec.Exact(0, 0),
            "a las 12 del mediodía" to TimeSpec.Exact(12, 0),
            "al mediodía" to TimeSpec.Exact(12, 0),
            "a medianoche" to TimeSpec.Exact(0, 0),
            "a las 3 de la madrugada" to TimeSpec.Exact(3, 0),
            "a las 7 am" to TimeSpec.Exact(7, 0),
            "a las 7 p. m." to TimeSpec.Exact(19, 0),
            "a las ocho en punto" to TimeSpec.MissingPeriod(8, 0),
            "a las ocho treinta" to TimeSpec.MissingPeriod(8, 30),
            "a las veinte y treinta" to TimeSpec.Exact(20, 30),
            "a las veintiuna horas" to TimeSpec.Exact(21, 0),
            "en 20 minutos" to TimeSpec.InMinutes(20),
            "en veinte minutos" to TimeSpec.InMinutes(20),
            "dentro de una hora" to TimeSpec.InMinutes(60),
            "en media hora" to TimeSpec.InMinutes(30),
            "en dos horas y media" to TimeSpec.InMinutes(150),
            "en la tarde" to TimeSpec.MissingHour(DayPeriod.AFTERNOON),
            "pon una alarma" to TimeSpec.MissingHour(null),
        )
        cases.forEach { (input, expected) ->
            assertEquals("Frase: \"$input\"", expected, extractor.extract(input).time)
        }
    }

    @Test
    fun `distingue manana como dia y como momento del dia`() {
        val cases = listOf(
            "mañana a las 8" to AlarmDay.TOMORROW,
            "a las 8 de la mañana" to AlarmDay.UNSPECIFIED,
            "mañana a las 8 de la mañana" to AlarmDay.TOMORROW,
            "hoy a las 5" to AlarmDay.TODAY,
            "esta noche a las 10" to AlarmDay.TODAY,
            "pasado mañana" to AlarmDay.UNSPECIFIED,
        )
        cases.forEach { (input, expected) ->
            assertEquals("Frase: \"$input\"", expected, extractor.extract(input).day)
        }
    }

    @Test
    fun `mañana a las 8 de la mañana es 8 AM del dia siguiente`() {
        val result = extractor.extract("mañana a las ocho de la mañana")
        assertEquals(TimeSpec.Exact(8, 0), result.time)
        assertEquals(AlarmDay.TOMORROW, result.day)
    }
}
