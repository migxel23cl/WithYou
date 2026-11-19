package cl.withyou.app.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.withyou.app.WithYouApp
import cl.withyou.app.data.prefs.PinRepository
import cl.withyou.app.domain.security.PinHasher
import cl.withyou.app.domain.security.PinPolicy
import cl.withyou.app.domain.security.StoredPin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * PIN del cuidador (§8 del diseño): protege Ajustes y los cambios en Contactos para evitar
 * cambios accidentales. Ver y llamar a un contacto no lo pide. Lo comparten las pantallas
 * protegidas (ligado a la Activity); se vuelve a bloquear al salir de ellas.
 */
class SecurityViewModel(
    private val repository: PinRepository,
    private val hasher: PinHasher = PinHasher(),
) : ViewModel() {

    /** Loading hasta leer DataStore, para no confundir "aún no se sabe" con "no hay PIN". */
    private val stored: StateFlow<StoredPinState> = repository.storedPin
        .map { if (it == null) StoredPinState.None else StoredPinState.Set(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StoredPinState.Loading)

    private val unlocked = MutableStateFlow(false)

    val uiState: StateFlow<SecurityUiState> = combine(stored, unlocked) { pin, isUnlocked ->
        when (pin) {
            StoredPinState.Loading -> SecurityUiState.LOADING
            StoredPinState.None -> SecurityUiState.NO_PIN
            is StoredPinState.Set -> if (isUnlocked) SecurityUiState.UNLOCKED else SecurityUiState.LOCKED
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SecurityUiState.LOADING)

    /** true si se puede hacer un cambio protegido sin pedir el PIN. */
    val canEdit: Boolean
        get() = uiState.value == SecurityUiState.NO_PIN || uiState.value == SecurityUiState.UNLOCKED

    /** Desbloquea si el PIN es correcto. */
    fun unlock(pin: String): Boolean {
        val current = stored.value as? StoredPinState.Set ?: return stored.value == StoredPinState.None
        val ok = hasher.matches(pin, current.pin)
        if (ok) unlocked.value = true
        return ok
    }

    /** Se llama al salir de una pantalla protegida. */
    fun lock() {
        unlocked.value = false
    }

    /** Crea o cambia el PIN. Solo se permite si no hay PIN o si ya se desbloqueó con el actual. */
    fun setPin(pin: String, confirmation: String): PinChangeResult {
        if (!canEdit) return PinChangeResult.LOCKED
        if (!PinPolicy.isValid(pin)) return PinChangeResult.INVALID
        if (pin != confirmation) return PinChangeResult.MISMATCH
        val hashed = hasher.hash(pin)
        unlocked.value = true
        viewModelScope.launch { repository.save(hashed) }
        return PinChangeResult.SAVED
    }

    fun removePin(): Boolean {
        if (!canEdit) return false
        viewModelScope.launch { repository.clear() }
        return true
    }

    private sealed interface StoredPinState {
        data object Loading : StoredPinState
        data object None : StoredPinState
        data class Set(val pin: StoredPin) : StoredPinState
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WithYouApp
                SecurityViewModel(app.container.pinRepository)
            }
        }
    }
}

enum class SecurityUiState { LOADING, NO_PIN, LOCKED, UNLOCKED }

enum class PinChangeResult { SAVED, INVALID, MISMATCH, LOCKED }
