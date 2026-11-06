package cl.withyou.app.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import cl.withyou.app.R
import cl.withyou.app.WithYouApp
import cl.withyou.app.databinding.ActivityMainBinding
import cl.withyou.app.ui.assistant.AssistantScreen
import cl.withyou.app.ui.assistant.AssistantViewModel
import cl.withyou.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    /** El mismo ViewModel que usan las pantallas del asistente (ligado a esta Activity). */
    private val assistant: AssistantViewModel by viewModels { AssistantViewModel.Factory }

    /** Preferencias de accesibilidad: el mismo ViewModel que usa la pantalla Ajustes. */
    private val settings: SettingsViewModel by viewModels { SettingsViewModel.Factory }

    /** Tamaño de texto con el que se dibujó esta Activity (RF-12). */
    private var appliedTextScale = 1f

    /**
     * Aplica el tamaño de texto elegido a toda la app, multiplicando el tamaño de letra del sistema.
     * Debe hacerse aquí, antes de crear cualquier vista, por eso se lee la preferencia de forma
     * sincrónica (es un archivo pequeño y local); aún no existen los ViewModels.
     */
    override fun attachBaseContext(newBase: Context) {
        val repository = (newBase.applicationContext as WithYouApp).container.preferencesRepository
        appliedTextScale = runBlocking { repository.preferences.first().textScale }
        val configuration = Configuration(newBase.resources.configuration)
        configuration.fontScale *= appliedTextScale
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        navController = navHost.navController

        // Pantallas de la barra inferior: primer nivel de navegación (RNF-02).
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.homeFragment, R.id.messagesFragment, R.id.contactsFragment, R.id.settingsFragment),
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNav.setupWithNavController(navController)

        // En Confirmar, Aclarar y Resultado se oculta la barra inferior para no distraer.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility = if (destination.id in ASSISTANT_DESTINATIONS) View.GONE else View.VISIBLE
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { assistant.uiState.map { it.screen }.distinctUntilChanged().collect(::showScreen) }
                // Si cambió el tamaño del texto en Ajustes, se vuelve a dibujar la app con el nuevo tamaño.
                launch {
                    settings.textScale.collect { scale ->
                        if (scale != appliedTextScale) recreate()
                    }
                }
            }
        }
    }

    /** Muestra la pantalla que pide la conversación. Todas quedan a un nivel de Inicio (RNF-02). */
    private fun showScreen(screen: AssistantScreen) {
        val current = navController.currentDestination?.id
        val target = when (screen) {
            AssistantScreen.HOME -> {
                if (current in ASSISTANT_DESTINATIONS) navController.popBackStack(R.id.homeFragment, false)
                return
            }
            AssistantScreen.CONFIRM -> R.id.confirmFragment
            AssistantScreen.CLARIFY -> R.id.clarifyFragment
            AssistantScreen.RESULT -> R.id.resultFragment
        }
        if (current == target) return
        navController.navigate(
            target,
            null,
            navOptions {
                popUpTo(R.id.homeFragment) { inclusive = false }
                launchSingleTop = true
            },
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        // En las pantallas del asistente, volver equivale a cancelar: lo decide el ViewModel.
        if (navController.currentDestination?.id in ASSISTANT_DESTINATIONS) {
            assistant.onBack()
            return true
        }
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private companion object {
        val ASSISTANT_DESTINATIONS = setOf(R.id.confirmFragment, R.id.clarifyFragment, R.id.resultFragment)
    }
}
