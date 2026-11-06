package cl.withyou.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.withyou.app.data.local.AppDatabase
import cl.withyou.app.data.repository.RoomContactRepository
import cl.withyou.app.domain.model.Contact
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba en el dispositivo: Room + RoomContactRepository con una base de datos en memoria. */
@RunWith(AndroidJUnit4::class)
class ContactRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RoomContactRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomContactRepository(database.contactDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun guardaYDevuelveOrdenadosPorNombreSinImportarMayusculas() = runTest {
        repository.save(Contact(0, "pedro", "912345678"))
        repository.save(Contact(0, "Ana", "923456789"))
        repository.save(Contact(0, "María", "934567890"))

        assertEquals(listOf("Ana", "María", "pedro"), repository.contacts.first().map { it.name })
    }

    @Test
    fun editaElMismoContactoSinDuplicarlo() = runTest {
        repository.save(Contact(0, "Pedro", "912345678"))
        val pedro = repository.contacts.first().single()

        repository.save(pedro.copy(name = "Pedro Soto", phone = "987654321"))

        assertEquals(listOf(Contact(pedro.id, "Pedro Soto", "987654321")), repository.contacts.first())
    }

    @Test
    fun eliminaPorId() = runTest {
        repository.save(Contact(0, "Pedro", "912345678"))
        repository.delete(repository.contacts.first().single().id)

        assertTrue(repository.contacts.first().isEmpty())
    }
}
