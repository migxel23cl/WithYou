package cl.withyou.app.ui.security

import cl.withyou.app.data.prefs.PinRepository
import cl.withyou.app.domain.security.PinHasher
import cl.withyou.app.domain.security.StoredPin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityViewModelTest {

    private val repository = InMemoryPinRepository()
    private lateinit var viewModel: SecurityViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SecurityViewModel(repository, PinHasher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sin PIN todo se puede editar`() {
        assertEquals(SecurityUiState.NO_PIN, viewModel.uiState.value)
        assertTrue(viewModel.canEdit)
    }

    @Test
    fun `crear un PIN lo guarda como hash y deja desbloqueado`() {
        assertEquals(PinChangeResult.SAVED, viewModel.setPin("4821", "4821"))
        assertEquals(SecurityUiState.UNLOCKED, viewModel.uiState.value)
        assertFalse(repository.storedPin.value!!.hash.contains("4821"))
    }

    @Test
    fun `el PIN debe ser valido y repetirse igual`() {
        assertEquals(PinChangeResult.INVALID, viewModel.setPin("12", "12"))
        assertEquals(PinChangeResult.MISMATCH, viewModel.setPin("1234", "1243"))
        assertNull(repository.storedPin.value)
    }

    @Test
    fun `al salir de la pantalla se bloquea y pide el PIN`() {
        viewModel.setPin("4821", "4821")
        viewModel.lock()
        assertEquals(SecurityUiState.LOCKED, viewModel.uiState.value)
        assertFalse(viewModel.canEdit)

        assertFalse(viewModel.unlock("0000"))
        assertEquals(SecurityUiState.LOCKED, viewModel.uiState.value)
        assertTrue(viewModel.unlock("4821"))
        assertTrue(viewModel.canEdit)
    }

    @Test
    fun `bloqueado no se puede cambiar ni quitar el PIN`() {
        viewModel.setPin("4821", "4821")
        viewModel.lock()
        assertEquals(PinChangeResult.LOCKED, viewModel.setPin("1111", "1111"))
        assertFalse(viewModel.removePin())
        assertTrue(viewModel.unlock("4821"))
    }

    @Test
    fun `desbloqueado se puede quitar el PIN`() {
        viewModel.setPin("4821", "4821")
        assertTrue(viewModel.removePin())
        assertEquals(SecurityUiState.NO_PIN, viewModel.uiState.value)
    }
}

private class InMemoryPinRepository : PinRepository {
    override val storedPin = MutableStateFlow<StoredPin?>(null)

    override suspend fun save(pin: StoredPin) {
        storedPin.value = pin
    }

    override suspend fun clear() {
        storedPin.value = null
    }
}
