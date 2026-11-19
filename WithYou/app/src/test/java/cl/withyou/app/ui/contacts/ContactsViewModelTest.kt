package cl.withyou.app.ui.contacts

import cl.withyou.app.data.repository.ContactRepository
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.domain.validation.ContactValidator
import cl.withyou.app.services.PhoneContact
import cl.withyou.app.services.PhoneContactReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
    private val agenda = FakePhoneContactReader()
    private lateinit var viewModel: ContactsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ContactsViewModel(repository, agenda)
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

    // region Agregar desde la agenda del teléfono

    @Test
    fun `agrega el contacto elegido en la agenda con el numero limpio`() = runTest {
        agenda.contacts["content://1"] = PhoneContact("María González", "+56 9 1234-5678")
        assertEquals(ContactImportResult.Imported("María González"), viewModel.importFromPhone("content://1"))
        assertEquals(listOf(Contact(1, "María González", "+56912345678")), viewModel.contacts.value)
    }

    @Test
    fun `no duplica un numero que ya esta en frecuentes`() = runTest {
        viewModel.save(null, "Mamá", "+56912345678")
        agenda.contacts["content://1"] = PhoneContact("María González", "+56 9 1234 5678")
        assertEquals(ContactImportResult.AlreadyExists("Mamá"), viewModel.importFromPhone("content://1"))
        assertEquals(1, viewModel.contacts.value.size)
    }

    @Test
    fun `rechaza contactos sin numero valido o sin nombre`() = runTest {
        agenda.contacts["content://1"] = PhoneContact("Banco", "*123#")
        agenda.contacts["content://2"] = PhoneContact("   ", "912345678")
        assertEquals(ContactImportResult.InvalidPhone, viewModel.importFromPhone("content://1"))
        assertEquals(ContactImportResult.InvalidName, viewModel.importFromPhone("content://2"))
        assertTrue(viewModel.contacts.value.isEmpty())
    }

    @Test
    fun `si no se puede leer el contacto lo informa`() = runTest {
        assertEquals(ContactImportResult.Failed, viewModel.importFromPhone("content://no-existe"))
    }

    @Test
    fun `un nombre muy largo se acorta en vez de rechazarse`() = runTest {
        agenda.contacts["content://1"] = PhoneContact("A".repeat(60), "912345678")
        val result = viewModel.importFromPhone("content://1") as ContactImportResult.Imported
        assertEquals(ContactValidator.MAX_NAME_LENGTH, result.name.length)
    }

    // endregion

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

private class FakePhoneContactReader : PhoneContactReader {
    val contacts = mutableMapOf<String, PhoneContact>()
    override suspend fun read(uri: String): PhoneContact? = contacts[uri]
}
