package cl.withyou.app.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import cl.withyou.app.R
import cl.withyou.app.databinding.FragmentPlaceholderBinding

/** Pantalla temporal para las secciones que aún no se implementan (Mensajes, Contactos, Ajustes). */
class PlaceholderFragment : Fragment(R.layout.fragment_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FragmentPlaceholderBinding.bind(view).placeholderText.setText(requireArguments().getInt(ARG_MESSAGE))
    }

    companion object {
        const val ARG_MESSAGE = "message"
    }
}
