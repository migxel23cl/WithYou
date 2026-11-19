package cl.withyou.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.withyou.app.WithYouApp
import cl.withyou.app.data.repository.ContactRepository
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.domain.validation.ContactValidator
import cl.withyou.app.services.PhoneContactReader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pantalla Contactos (CU-07, RF-11): lista, agregar, editar y eliminar contactos frecuentes. */
class ContactsViewModel(
    private val repository: ContactRepository,
    private val phoneContacts: PhoneContactReader,
) : ViewModel() {

    /** Lista ordenada por nombre; se actualiza sola al guardar o eliminar. */
    val contacts: StateFlow<List<Contact>> =
        repository.contacts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** El permiso de llamadas se explica una sola vez por sesión, para no insistir. */
    private var callPermissionExplained = false

    /**
     * Valida y guarda. [id] null o 0 crea un contacto nuevo; si no, edita ese contacto.
     * El número se guarda limpio ("+56 9 1234 5678" → "+56912345678").
     */
    fun save(id: Long?, name: String, phone: String): ContactFormResult {
        val cleanName = ContactValidator.cleanName(name) ?: return ContactFormResult.NAME_INVALID
        val cleanPhone = ContactValidator.sanitizePhone(phone) ?: return ContactFormResult.PHONE_INVALID
        viewModelScope.launch { repository.save(Contact(id = id ?: 0, name = cleanName, phone = cleanPhone)) }
        return ContactFormResult.SAVED
    }

    /**
     * Agrega el contacto elegido en la agenda del teléfono. Se valida igual que en el formulario
     * y no se duplica si ese número ya está entre los frecuentes.
     */
    suspend fun importFromPhone(uri: String): ContactImportResult {
        val picked = phoneContacts.read(uri) ?: return ContactImportResult.Failed
        val phone = ContactValidator.sanitizePhone(picked.phone) ?: return ContactImportResult.InvalidPhone
        val name = ContactValidator.cleanName(picked.name.take(ContactValidator.MAX_NAME_LENGTH))
            ?: return ContactImportResult.InvalidName
        contacts.value.firstOrNull { it.phone == phone }?.let { return ContactImportResult.AlreadyExists(it.name) }
        repository.save(Contact(id = 0, name = name, phone = phone))
        return ContactImportResult.Imported(name)
    }

    fun delete(contact: Contact) {
        viewModelScope.launch { repository.delete(contact.id) }
    }

    /** true la primera vez: la pantalla explica para qué es el permiso de llamadas antes de pedirlo (RNF-06). */
    fun shouldExplainCallPermission(): Boolean {
        if (callPermissionExplained) return false
        callPermissionExplained = true
        return true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WithYouApp
                ContactsViewModel(app.container.contactRepository, app.container.phoneContactReader)
            }
        }
    }
}

enum class ContactFormResult { SAVED, NAME_INVALID, PHONE_INVALID }

/** Resultado de "Agregar desde mis contactos". */
sealed interface ContactImportResult {
    data class Imported(val name: String) : ContactImportResult
    data class AlreadyExists(val name: String) : ContactImportResult
    data object InvalidPhone : ContactImportResult
    data object InvalidName : ContactImportResult
    data object Failed : ContactImportResult
}
