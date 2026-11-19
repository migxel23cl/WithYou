package cl.withyou.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Clave de la base de datos cifrada (RNF-05, §8: "clave en Android Keystore").
 *
 * La primera vez se genera una clave aleatoria de 32 bytes para SQLCipher. Esa clave se guarda
 * cifrada con una llave AES que vive dentro del Android Keystore: la llave nunca sale del
 * almacén seguro del sistema, así que copiar los archivos de la app no basta para leer los datos.
 */
class DatabasePassphrase(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** true si se tuvo que crear una clave nueva (la base de datos anterior ya no se puede abrir). */
    var isNew: Boolean = false
        private set

    fun get(): ByteArray {
        val stored = prefs.getString(KEY_ENCRYPTED, null)
        if (stored != null) {
            try {
                return decrypt(stored)
            } catch (e: GeneralSecurityException) {
                // Puede pasar si se restauró la app en otro equipo: no se puede leer, se crea otra.
                Log.w(TAG, "No se pudo leer la clave de la base de datos; se crea una nueva", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Clave de la base de datos dañada; se crea una nueva", e)
            }
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        prefs.edit().putString(KEY_ENCRYPTED, encrypt(passphrase)).apply()
        isNew = true
        return passphrase
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Guarda IV + texto cifrado en Base64. */
    private fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keystoreKey()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): ByteArray {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
    }

    private companion object {
        const val TAG = "WithYou.Db"
        const val PREFS = "database_key"
        const val KEY_ENCRYPTED = "encrypted_passphrase"
        const val KEY_ALIAS = "withyou_database_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
