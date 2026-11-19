package cl.withyou.app.ui

import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.withyou.app.R
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.ui.assistant.AssistantViewModel
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas de interfaz (Espresso) en el teléfono. Cada toque pasa además por el verificador de
 * accesibilidad de Google: tamaño táctil, nombre para TalkBack y contraste (RNF-01, RNF-08).
 *
 * No cambian datos: los formularios se cancelan y el PIN del cuidador se restaura al terminar.
 */
@RunWith(AndroidJUnit4::class)
class AppUiTest {

    /** Primero se quita el PIN (y se restaura al final); después se abre la app. */
    private val withoutPin = WithoutPinRule()
    private val activity = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(withoutPin).around(activity)

    private val activityRule get() = activity

    @Test
    fun inicioMuestraElMicrofonoYLasAccionesRapidas() {
        onView(withId(R.id.question_text)).check(matches(withText(R.string.home_question)))
        onView(withId(R.id.mic_button))
            .check(matches(isDisplayed()))
            .check(matches(withContentDescription(R.string.home_mic_description)))
        onView(withId(R.id.action_alarm)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.action_flashlight)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun lasCuatroPestanasAbrenSuPantalla() {
        onView(withId(R.id.messagesFragment)).perform(click())
        onView(withText(R.string.messages_question)).check(matches(isDisplayed()))

        onView(withId(R.id.contactsFragment)).perform(click())
        onView(withId(R.id.add_contact_button)).check(matches(isDisplayed()))

        onView(withId(R.id.settingsFragment)).perform(click())
        onView(withId(R.id.text_scale_slider)).check(matches(isDisplayed()))

        onView(withId(R.id.homeFragment)).perform(click())
        onView(withId(R.id.mic_button)).check(matches(isDisplayed()))
    }

    @Test
    fun elFormularioDeContactoMarcaLosErroresSinCerrarse() {
        onView(withId(R.id.contactsFragment)).perform(click())
        onView(withId(R.id.add_contact_button)).perform(click())

        // Con el teclado abierto el diálogo se achica: se desplaza hasta cada campo, como haría una persona.
        onView(withId(R.id.name_input)).inRoot(isDialog()).perform(scrollTo(), replaceText("Prueba"), closeSoftKeyboard())
        onView(withId(R.id.phone_input)).inRoot(isDialog()).perform(scrollTo(), replaceText("123"), closeSoftKeyboard())
        onView(withText(R.string.button_save)).inRoot(isDialog()).perform(click())

        onView(withText(R.string.error_phone_invalid)).inRoot(isDialog()).check(matches(isDisplayed()))
        Espresso.closeSoftKeyboard()
        onView(withText(R.string.button_cancel)).inRoot(isDialog()).perform(click())
    }

    /** Responsive: en una pantalla de 540x960 con letra grande, los botones del diálogo se ven completos. */
    @Test
    fun crearPinMuestraLosDosCamposYLosBotonesCompletos() {
        onView(withId(R.id.settingsFragment)).perform(click())
        onView(withId(R.id.set_pin_button)).perform(scrollTo(), click())

        onView(withId(R.id.dialog_positive_button)).inRoot(isDialog())
            .check(matches(isCompletelyDisplayed())).check(matches(withText(R.string.button_save)))
        onView(withId(R.id.dialog_negative_button)).inRoot(isDialog()).check(matches(isCompletelyDisplayed()))
        onView(withId(R.id.confirm_input)).inRoot(isDialog()).perform(scrollTo()).check(matches(isDisplayed()))
        Espresso.closeSoftKeyboard()
        onView(withId(R.id.dialog_negative_button)).inRoot(isDialog()).perform(click())
    }

    @Test
    fun elFormularioDeContactoTieneLosBotonesCompletos() {
        onView(withId(R.id.contactsFragment)).perform(click())
        onView(withId(R.id.add_contact_button)).perform(click())
        onView(withId(R.id.dialog_positive_button)).inRoot(isDialog()).check(matches(isCompletelyDisplayed()))
        onView(withId(R.id.dialog_negative_button)).inRoot(isDialog()).check(matches(isCompletelyDisplayed()))
        Espresso.closeSoftKeyboard()
        onView(withId(R.id.dialog_negative_button)).inRoot(isDialog()).perform(click())
    }

    @Test
    fun unaLlamadaPideConfirmacionYCancelarVuelveAlInicio() {
        activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity, AssistantViewModel.Factory)[AssistantViewModel::class.java]
                .requestCall(Contact(0, "Prueba Espresso", "912345678"))
        }

        onView(withId(R.id.confirm_button)).check(matches(withText(R.string.confirm_call_button)))
        onView(withId(R.id.cancel_button)).perform(click())

        onView(withId(R.id.response_text)).check(matches(withText(R.string.cancelled)))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun enableAccessibilityChecks() {
            AccessibilityChecks.enable().setRunChecksFromRootView(true)
        }
    }
}
