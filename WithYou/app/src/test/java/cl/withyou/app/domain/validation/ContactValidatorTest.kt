package cl.withyou.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactValidatorTest {

    @Test
    fun `limpia numeros de telefono validos`() {
        val cases = mapOf(
            "+56 9 1234 5678" to "+56912345678",
            "9-1234-5678" to "912345678",
            "(2) 2345 6789" to "223456789",
            "  912345678  " to "912345678",
        )
        cases.forEach { (raw, expected) ->
            assertEquals("Número: \"$raw\"", expected, ContactValidator.sanitizePhone(raw))
        }
    }

    @Test
    fun `rechaza numeros invalidos`() {
        listOf("1234", "*123#", "tel:912345678", "abc", "", "1234567890123456").forEach { raw ->
            assertNull("Número: \"$raw\"", ContactValidator.sanitizePhone(raw))
        }
    }

    @Test
    fun `limpia nombres y rechaza los invalidos`() {
        assertEquals("María González", ContactValidator.cleanName("  María   González "))
        assertEquals("Hija", ContactValidator.cleanName("Hija"))
        assertNull(ContactValidator.cleanName("   "))
        assertNull(ContactValidator.cleanName("12345"))
        assertNull(ContactValidator.cleanName("a".repeat(ContactValidator.MAX_NAME_LENGTH + 1)))
    }
}
