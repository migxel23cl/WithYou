package cl.withyou.app.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val hasher = PinHasher()

    @Test
    fun `reconoce el PIN correcto y rechaza los demas`() {
        val stored = hasher.hash("4821")
        assertTrue(hasher.matches("4821", stored))
        listOf("4822", "1284", "", "48211").forEach { assertFalse("PIN $it", hasher.matches(it, stored)) }
    }

    @Test
    fun `no guarda el PIN y usa una sal distinta cada vez`() {
        val first = hasher.hash("1234")
        val second = hasher.hash("1234")
        assertFalse(first.hash.contains("1234"))
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
    }

    @Test
    fun `un PIN guardado danado no se acepta ni falla`() {
        assertFalse(hasher.matches("1234", StoredPin("no-es-base64!!", "tampoco!!")))
    }

    @Test
    fun `el PIN tiene exactamente 4 numeros`() {
        assertTrue(PinPolicy.isValid("0042"))
        listOf("123", "12345", "12a4", "", "١٢٣٤").forEach { assertFalse("PIN $it", PinPolicy.isValid(it)) }
    }
}
