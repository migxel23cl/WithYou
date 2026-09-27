package cl.withyou.app.domain.parser

import cl.withyou.app.domain.model.Contact
import cl.withyou.app.domain.model.ContactTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactMatcherTest {

    private val matcher = ContactMatcher()

    private val maria = Contact(1, "María González", "+56911111111")
    private val mariaPerez = Contact(2, "María Pérez", "+56922222222")
    private val pedro = Contact(3, "Pedro Soto", "+56933333333")
    private val hija = Contact(4, "Hija", "+56944444444")
    private val mario = Contact(5, "Mario", "+56955555555")

    @Test
    fun `encuentra por nombre de pila sin tildes`() {
        assertEquals(ContactTarget.Resolved(maria), matcher.match("maria", listOf(maria, pedro)))
    }

    @Test
    fun `tolera errores leves de reconocimiento`() {
        assertEquals(ContactTarget.Resolved(pedro), matcher.match("Pedrp", listOf(maria, pedro)))
    }

    @Test
    fun `dos contactos con el mismo nombre son ambiguos`() {
        val result = matcher.match("María", listOf(maria, mariaPerez, pedro))
        assertTrue(result is ContactTarget.Ambiguous)
        assertEquals(setOf(maria, mariaPerez), (result as ContactTarget.Ambiguous).options.toSet())
    }

    @Test
    fun `nombre completo resuelve la ambiguedad`() {
        assertEquals(
            ContactTarget.Resolved(maria),
            matcher.match("María González", listOf(maria, mariaPerez)),
        )
    }

    @Test
    fun `prefiere la coincidencia exacta sobre una parecida`() {
        assertEquals(ContactTarget.Resolved(mario), matcher.match("Mario", listOf(maria, mario)))
    }

    @Test
    fun `contactos por parentesco`() {
        assertEquals(ContactTarget.Resolved(hija), matcher.match("hija", listOf(maria, hija)))
    }

    @Test
    fun `nombre desconocido no se encuentra`() {
        val result = matcher.match("Roberto", listOf(maria, pedro))
        assertEquals(ContactTarget.NotFound("Roberto", emptyList()), result)
    }

    @Test
    fun `sin contactos no se encuentra`() {
        assertEquals(ContactTarget.NotFound("Pedro"), matcher.match("Pedro", emptyList()))
    }
}
