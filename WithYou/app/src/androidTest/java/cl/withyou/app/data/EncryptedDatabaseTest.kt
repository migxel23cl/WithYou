package cl.withyou.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.withyou.app.data.local.AppDatabase
import cl.withyou.app.data.local.DatabasePassphrase
import cl.withyou.app.data.repository.RoomContactRepository
import cl.withyou.app.domain.model.Contact
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba en el dispositivo: los contactos quedan cifrados en el disco (RNF-05). */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "test-encrypted.db"

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(name)
    }

    private fun open(key: ByteArray): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(key))
            .build()

    @Test
    fun elArchivoNoEsLegibleSinLaClave() = runTest {
        val key = ByteArray(32) { it.toByte() }
        val database = open(key)
        RoomContactRepository(database.contactDao()).save(Contact(0, "María Secreta", "912345678"))
        database.close()

        val bytes = context.getDatabasePath(name).readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse("No debe tener la cabecera de SQLite sin cifrar", text.startsWith("SQLite format 3"))
        assertFalse("El nombre no debe verse en el archivo", text.contains("María Secreta") || text.contains("Secreta"))
        assertFalse("El número no debe verse en el archivo", text.contains("912345678"))
    }

    @Test
    fun conLaClaveCorrectaSeLeenLosDatos() = runTest {
        val key = ByteArray(32) { (it * 7).toByte() }
        open(key).also { RoomContactRepository(it.contactDao()).save(Contact(0, "Pedro", "912345678")) }.close()

        val reopened = open(key)
        assertEquals(listOf("Pedro"), RoomContactRepository(reopened.contactDao()).contacts.first().map { it.name })
        reopened.close()
    }

    @Test
    fun conOtraClaveNoSePuedeAbrir() = runTest {
        open(ByteArray(32) { 1 }).also { RoomContactRepository(it.contactDao()).save(Contact(0, "Pedro", "912345678")) }.close()

        val wrong = open(ByteArray(32) { 2 })
        val failed = runCatching { RoomContactRepository(wrong.contactDao()).contacts.first() }.isFailure
        wrong.close()
        assertTrue("Una clave distinta no debe abrir la base de datos", failed)
    }

    @Test
    fun laClaveSeGuardaEnElKeystoreYSeRecupera() {
        val first = DatabasePassphrase(context).get()
        val second = DatabasePassphrase(context).get()
        assertEquals(32, first.size)
        assertArrayEquals("La clave debe ser la misma al volver a abrir la app", first, second)
    }
}
