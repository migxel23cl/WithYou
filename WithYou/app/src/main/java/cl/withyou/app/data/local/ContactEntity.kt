package cl.withyou.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import cl.withyou.app.domain.model.Contact

/** Fila de la tabla de contactos frecuentes (CU-07). */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
) {
    fun toDomain() = Contact(id = id, name = name, phone = phone)

    companion object {
        fun from(contact: Contact) = ContactEntity(id = contact.id, name = contact.name, phone = contact.phone)
    }
}
