package cl.withyou.app.services

/** Contacto tal como está en la agenda del teléfono (aún sin validar). */
data class PhoneContact(val name: String, val phone: String)

/**
 * Lee el contacto que el usuario eligió en la agenda del teléfono. Detrás de una interfaz para
 * poder reemplazarlo en las pruebas.
 */
interface PhoneContactReader {

    /** [uri] es el que devuelve la agenda al elegir un número. null si no se pudo leer. */
    suspend fun read(uri: String): PhoneContact?
}
