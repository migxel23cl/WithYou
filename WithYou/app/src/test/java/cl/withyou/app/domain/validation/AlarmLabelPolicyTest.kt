package cl.withyou.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmLabelPolicyTest {

    @Test
    fun `limpia espacios y signos y pone mayuscula`() {
        assertEquals("Tomar la pastilla", AlarmLabelPolicy.clean("  tomar   la pastilla.  "))
        assertEquals("Ya almorzaste", AlarmLabelPolicy.clean("¿ya almorzaste?"))
        assertEquals("Médico", AlarmLabelPolicy.clean("«médico»"))
    }

    @Test
    fun `acorta los nombres largos y rechaza los que no tienen letras`() {
        assertEquals(AlarmLabelPolicy.MAX_LENGTH, AlarmLabelPolicy.clean("a".repeat(80))!!.length)
        assertNull(AlarmLabelPolicy.clean("   "))
        assertNull(AlarmLabelPolicy.clean("123"))
        assertNull(AlarmLabelPolicy.clean(null))
    }
}
