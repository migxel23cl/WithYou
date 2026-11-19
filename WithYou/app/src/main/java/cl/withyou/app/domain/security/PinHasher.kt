package cl.withyou.app.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN guardado: nunca el PIN mismo, solo una "sal" aleatoria y el hash derivado (en Base64). */
data class StoredPin(val salt: String, val hash: String)

/** Reglas del PIN del cuidador (§8 del diseño): 4 dígitos. */
object PinPolicy {
    const val LENGTH = 4

    fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }
}

/**
 * Deriva y compara PINs con PBKDF2 (estándar para contraseñas): aunque alguien lea el archivo
 * de ajustes, no puede obtener el PIN. Kotlin puro (javax.crypto), sin dependencias de Android.
 */
class PinHasher(private val random: SecureRandom = SecureRandom()) {

    fun hash(pin: String): StoredPin {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return StoredPin(encode(salt), encode(derive(pin, salt)))
    }

    /** Compara en tiempo constante, para no dar pistas por cuánto tarda. */
    fun matches(pin: String, stored: StoredPin): Boolean {
        val salt = decode(stored.salt) ?: return false
        val expected = decode(stored.hash) ?: return false
        return MessageDigest.isEqual(derive(pin, salt), expected)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray? = try {
        Base64.getDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        /** PBKDF2WithHmacSHA1 está disponible desde Android 8 (minSdk 26) y en la JVM de las pruebas. */
        const val ALGORITHM = "PBKDF2WithHmacSHA1"

        /** Suficiente para un PIN de cuidador y rápido (~0,1 s) en un teléfono de gama baja. */
        const val ITERATIONS = 10_000
        const val KEY_BITS = 256
        const val SALT_BYTES = 16
    }
}
