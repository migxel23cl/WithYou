package cl.withyou.app.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `normaliza tildes, signos, mayusculas y espacios`() {
        val cases = mapOf(
            "¿Qué hora es?" to "que hora es",
            "  Llámame   a   María,  por favor  " to "llamame a maria por favor",
            "Enciende la LINTERNA!" to "enciende la linterna",
            "Mañana a las 8:30" to "manana a las 8:30",
            "a las 8.30" to "a las 8:30",
            "Hora: 7" to "hora 7",
            "a las 8 a. m." to "a las 8 am",
            "a las 9 p.m." to "a las 9 pm",
            "Ñuñoa" to "nunoa",
            "" to "",
        )
        cases.forEach { (input, expected) ->
            assertEquals("Frase: \"$input\"", expected, TextNormalizer.normalize(input))
        }
    }

    @Test
    fun `conserva la palabra original de cada token`() {
        val tokens = TextNormalizer.tokenize("Dile a Pedro: ¡llego tarde!")
        assertEquals(listOf("dile", "a", "pedro", "llego", "tarde"), tokens.map { it.normalized })
        assertEquals("Pedro:", tokens[2].original)
        assertEquals(4, tokens[4].sourceIndex)
    }
}
