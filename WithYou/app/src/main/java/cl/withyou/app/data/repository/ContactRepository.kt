package cl.withyou.app.data.repository

import cl.withyou.app.data.local.ContactDao
import cl.withyou.app.data.local.ContactEntity
import cl.withyou.app.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Acceso a los contactos frecuentes (Repository de MVVM). Los ViewModels dependen de esta
 * interfaz y no de Room, así se pueden probar con un repositorio falso.
 */
interface ContactRepository {

    /** Lista siempre actualizada, ordenada por nombre. */
    val contacts: Flow<List<Contact>>

    /** Crea el contacto si su id es 0; si no, lo actualiza. */
    suspend fun save(contact: Contact)

    suspend fun delete(id: Long)
}

class RoomContactRepository(private val dao: ContactDao) : ContactRepository {

    override val contacts: Flow<List<Contact>> =
        dao.observeAll().map { entities -> entities.map(ContactEntity::toDomain) }

    override suspend fun save(contact: Contact) {
        dao.upsert(ContactEntity.from(contact))
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
