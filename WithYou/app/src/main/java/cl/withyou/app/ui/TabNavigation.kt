package cl.withyou.app.ui

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import cl.withyou.app.R

/** Abre una pestaña de la barra inferior igual que si se tocara (Inicio queda como base, RNF-02). */
fun Fragment.openTab(@IdRes destinationId: Int) {
    val options = NavOptions.Builder()
        .setLaunchSingleTop(true)
        .setPopUpTo(R.id.homeFragment, inclusive = false, saveState = true)
        .setRestoreState(true)
        .build()
    findNavController().navigate(destinationId, null, options)
}
