package cl.withyou.app.ui.contacts

import cl.withyou.app.data.repository.ContactRepository
import cl.withyou.app.domain.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val repository = InMemoryContactRepository()
    private lateinit var viewModel: ContactsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ContactsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `guarda un contacto con el numero limpio`() {
        assertEquals(ContactFormResult.SAVED, viewModel.save(null, "  María  ", "+56 9 1234 5678"))
        assertEquals(listOf(Contact(1, "María", "+56912345678")), viewModel.contacts.value)
    }

    @Test
    fun `no guarda si el nombre o el numero no son validos`() {
        assertEquals(ContactFormResult.NAME_INVALID, viewModel.save(null, "  ", "912345678"))
        assertEquals(ContactFormResult.PHONE_INVALID, viewModel.save(null, "Pedro", "123"))
        assertTrue(viewModel.contacts.value.isEmpty())
    }

    @Test
    fun `edita y elimina`() {
        viewModel.save(null, "Pedro", "912345678")
        val pedro = viewModel.contacts.value.single()
        viewModel.save(pedro.id, "Pedro Soto", "987654321")
        assertEquals(listOf(Contact(pedro.id, "Pedro Soto", "987654321")), viewModel.contacts.value)

        viewModel.delete(viewModel.contacts.value.single())
        assertTrue(viewModel.contacts.value.isEmpty())
    }

    @Test
    fun `el permiso de llamadas se explica una sola vez`() {
        assertTrue(viewModel.shouldExplainCallPermission())
        assertFalse(viewModel.shouldExplainCallPermission())
    }
}

/** Repositorio en memoria que se comporta como Room: ids automáticos y lista ordenada por nombre. */
class InMemoryContactRepository(initial: List<Contact> = emptyList()) : ContactRepository {
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
    override val contacts = MutableStateFlow(initial.sortedBy { it.name.lowercase() })

    override suspend fun save(contact: Contact) {
        val saved = if (contact.id == 0L) contact.copy(id = nextId++) else contact
        contacts.value = (contacts.value.filterNot { it.id == saved.id } + saved).sortedBy { it.name.lowercase() }
    }

    override suspend fun delete(id: Long) {
        contacts.value = contacts.value.filterNot { it.id == id }
    }
}
