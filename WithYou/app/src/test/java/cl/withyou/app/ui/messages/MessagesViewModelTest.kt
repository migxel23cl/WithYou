package cl.withyou.app.ui.messages

import cl.withyou.app.domain.model.Contact
import cl.withyou.app.ui.contacts.InMemoryContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {

    private val pedro = Contact(1, "Pedro", "912345678")
    private val ana = Contact(2, "Ana", "923456789")
    private val repository = InMemoryContactRepository(listOf(pedro, ana))
    private lateinit var viewModel: MessagesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = MessagesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `muestra los contactos y ninguno elegido al inicio`() {
        assertEquals(MessagesUiState(listOf(ana, pedro), selected = null), viewModel.uiState.value)
    }

    @Test
    fun `elegir un contacto lo marca`() {
        viewModel.select(pedro)
        assertEquals(pedro, viewModel.uiState.value.selected)
        viewModel.select(ana)
        assertEquals(ana, viewModel.uiState.value.selected)
    }

    @Test
    fun `si se elimina el contacto elegido deja de estar elegido`() = runTest {
        viewModel.select(pedro)
        repository.delete(pedro.id)
        assertNull(viewModel.uiState.value.selected)
    }
}
