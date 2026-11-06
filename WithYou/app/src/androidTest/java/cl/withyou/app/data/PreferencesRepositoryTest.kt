package cl.withyou.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.withyou.app.data.prefs.DataStorePreferencesRepository
import cl.withyou.app.domain.model.AccessibilityPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Prueba en el dispositivo: DataStore real escribiendo en un archivo temporal (no toca los ajustes de la app). */
@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryTest {

    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private lateinit var repository: DataStorePreferencesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        file = File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb")
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        repository = DataStorePreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun sinDatosGuardadosEntregaLosValoresPorDefecto() = runTest {
        assertEquals(AccessibilityPreferences(), repository.preferences.first())
    }

    @Test
    fun guardaYLeeLasTresPreferencias() = runTest {
        repository.setTextScale(1.4f)
        repository.setVoiceVolume(0.6f)
        repository.setVoiceFeedback(false)

        assertEquals(AccessibilityPreferences(1.4f, 0.6f, false), repository.preferences.first())
    }

    @Test
    fun limitaLosValoresFueraDeRango() = runTest {
        repository.setTextScale(5f)
        repository.setVoiceVolume(0f)

        val stored = repository.preferences.first()
        assertEquals(AccessibilityPreferences.MAX_TEXT_SCALE, stored.textScale)
        assertEquals(AccessibilityPreferences.MIN_VOICE_VOLUME, stored.voiceVolume)
    }
}
