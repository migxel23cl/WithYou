package cl.withyou.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.withyou.app.WithYouApp
import cl.withyou.app.data.repository.ContactRepository
import cl.withyou.app.domain.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Pantalla Mensajes (mockup 3): "¿A quién le escribes?" con los contactos frecuentes para elegir. */
class MessagesViewModel(repository: ContactRepository) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<MessagesUiState> =
        combine(repository.contacts, selectedId) { contacts, id ->
            // Si el contacto elegido se eliminó, ya no queda elegido.
            MessagesUiState(contacts, contacts.firstOrNull { it.id == id })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MessagesUiState())

    fun select(contact: Contact) {
        selectedId.value = contact.id
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WithYouApp
                MessagesViewModel(app.container.contactRepository)
            }
        }
    }
}

data class MessagesUiState(
    val contacts: List<Contact> = emptyList(),
    val selected: Contact? = null,
)
