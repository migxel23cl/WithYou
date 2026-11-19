package cl.withyou.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.withyou.app.R
import cl.withyou.app.WithYouApp
import cl.withyou.app.domain.model.AccessibilityPreferences
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.ui.assistant.AssistantViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diseño responsive en el peor caso: texto al 160 % (máximo de Ajustes) en la pantalla del
 * teléfono de prueba (540x960). Los botones principales deben verse completos sin desplazarse.
 * Al terminar se restaura el tamaño de texto que tenía el usuario.
 */
@RunWith(AndroidJUnit4::class)
class ResponsiveUiTest {

    @get:Rule
    val withoutPin = WithoutPinRule()

    private val preferences = ApplicationProvider.getApplicationContext<WithYouApp>().container.preferencesRepository
    private var previousScale = AccessibilityPreferences.DEFAULT_TEXT_SCALE

    @Before
    fun useLargestText() = runBlocking {
        previousScale = preferences.preferences.first().textScale
        preferences.setTextScale(AccessibilityPreferences.MAX_TEXT_SCALE)
    }

    @After
    fun restoreText() = runBlocking {
        preferences.setTextScale(previousScale)
    }

    @Test
    fun confirmarMuestraSiYCancelarCompletos() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity, AssistantViewModel.Factory)[AssistantViewModel::class.java]
                    .requestCall(Contact(0, "María José González", "912345678"))
            }
            onView(withId(R.id.confirm_button)).check(matches(isCompletelyDisplayed()))
            onView(withId(R.id.cancel_button)).check(matches(isCompletelyDisplayed())).perform(click())
        }
    }

    @Test
    fun losDialogosMuestranSusBotonesCompletos() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.settingsFragment)).perform(click())
            onView(withId(R.id.set_pin_button)).perform(scrollTo(), click())
            Espresso.closeSoftKeyboard()
            onView(withId(R.id.dialog_positive_button)).inRoot(isDialog()).check(matches(isCompletelyDisplayed()))
            onView(withId(R.id.dialog_negative_button)).inRoot(isDialog())
                .check(matches(isCompletelyDisplayed())).perform(click())

            onView(withId(R.id.contactsFragment)).perform(click())
            onView(withId(R.id.add_contact_button)).perform(click())
            Espresso.closeSoftKeyboard()
            onView(withId(R.id.dialog_positive_button)).inRoot(isDialog()).check(matches(isCompletelyDisplayed()))
            onView(withId(R.id.dialog_negative_button)).inRoot(isDialog())
                .check(matches(isCompletelyDisplayed())).perform(click())
        }
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun enableAccessibilityChecks() {
            AccessibilityChecks.enable().setRunChecksFromRootView(true)
        }
    }
}
