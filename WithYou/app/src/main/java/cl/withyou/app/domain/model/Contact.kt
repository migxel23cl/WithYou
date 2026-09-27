package cl.withyou.app.domain.model

/** Contacto frecuente registrado por el usuario o su cuidador (CU-07). */
data class Contact(
    val id: Long,
    val name: String,
    val phone: String,
)

/** Resultado de buscar en los contactos frecuentes el nombre que dijo el usuario. */
sealed interface ContactTarget {

    /** Se encontró un único contacto. */
    data class Resolved(val contact: Contact) : ContactTarget

    /** Varios contactos coinciden (p. ej. dos "María"): hay que pedir que elija. */
    data class Ambiguous(val spokenName: String, val options: List<Contact>) : ContactTarget

    /**
     * No se encontró el contacto. [spokenName] es null si el usuario no dijo a quién.
     * [suggestions] son contactos parecidos para ofrecer "¿Quisiste decir…?".
     */
    data class NotFound(
        val spokenName: String?,
        val suggestions: List<Contact> = emptyList(),
    ) : ContactTarget
}
