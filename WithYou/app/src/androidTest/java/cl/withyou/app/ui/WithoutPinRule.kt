package cl.withyou.app.ui

import androidx.test.core.app.ApplicationProvider
import cl.withyou.app.WithYouApp
import cl.withyou.app.domain.security.StoredPin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Las pruebas de interfaz no deben depender de si el usuario dejó un PIN del cuidador: esta regla
 * lo quita durante la prueba y al terminar lo deja exactamente como estaba.
 */
class WithoutPinRule : ExternalResource() {

    private val pins = ApplicationProvider.getApplicationContext<WithYouApp>().container.pinRepository
    private var saved: StoredPin? = null

    override fun before() = runBlocking {
        saved = pins.storedPin.first()
        pins.clear()
    }

    override fun after() = runBlocking {
        saved?.let { pins.save(it) }
        Unit
    }
}
