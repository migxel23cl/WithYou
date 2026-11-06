package cl.withyou.app.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import cl.withyou.app.R
import cl.withyou.app.databinding.DialogContactFormBinding
import cl.withyou.app.databinding.FragmentContactsBinding
import cl.withyou.app.domain.model.Contact
import cl.withyou.app.ui.assistant.AssistantViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Pantalla Contactos (CU-07, mockup 5). Solo muestra y avisa: validar y guardar lo hace
 * ContactsViewModel, y llamar lo hace AssistantViewModel (pasando por Confirmar, RF-10).
 */
class ContactsFragment : Fragment(R.layout.fragment_contacts) {

    private var binding: FragmentContactsBinding? = null
    private val viewModel: ContactsViewModel by viewModels { ContactsViewModel.Factory }
    private val assistant: AssistantViewModel by activityViewModels { AssistantViewModel.Factory }

    private val adapter = ContactsAdapter(onEdit = ::showForm, onCall = { assistant.requestCall(it) })

    /** Si se niega, igual se puede llamar: se abre el teléfono con el número marcado. */
    private val requestCallPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentContactsBinding.bind(view).also { binding = it }

        binding.contactsList.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsList.adapter = adapter
        binding.addContactButton.setOnClickListener { showForm(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.contacts.collect { contacts ->
                    adapter.submitList(contacts)
                    binding.emptyText.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        binding?.contactsList?.adapter = null
        binding = null
        super.onDestroyView()
    }

    /** Formulario para agregar ([contact] null) o editar un contacto. */
    private fun showForm(contact: Contact?) {
        val form = DialogContactFormBinding.inflate(LayoutInflater.from(requireContext()))
        form.nameInput.setText(contact?.name)
        form.phoneInput.setText(contact?.phone)

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (contact == null) R.string.contact_form_title_new else R.string.contact_form_title_edit)
            .setView(form.root)
            .setPositiveButton(R.string.button_save, null)
            .setNegativeButton(R.string.button_cancel, null)
        if (contact != null) builder.setNeutralButton(R.string.button_delete) { _, _ -> confirmDelete(contact) }
        val dialog = builder.show()

        // Se reemplaza el listener para que el diálogo no se cierre si hay errores.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val result = viewModel.save(contact?.id, form.nameInput.text.toString(), form.phoneInput.text.toString())
            form.nameLayout.error = if (result == ContactFormResult.NAME_INVALID) getString(R.string.error_name_missing) else null
            form.phoneLayout.error = if (result == ContactFormResult.PHONE_INVALID) getString(R.string.error_phone_invalid) else null
            if (result == ContactFormResult.SAVED) {
                dialog.dismiss()
                showMessage(R.string.contact_saved)
                offerCallPermission()
            }
        }
    }

    private fun confirmDelete(contact: Contact) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_contact_title, contact.name))
            .setMessage(R.string.delete_contact_message)
            .setPositiveButton(R.string.button_delete) { _, _ ->
                viewModel.delete(contact)
                showMessage(R.string.contact_deleted)
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    /** Explica en lenguaje simple para qué es el permiso de llamadas antes de pedirlo (RNF-06). */
    private fun offerCallPermission() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        if (granted || !viewModel.shouldExplainCallPermission()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.call_permission_title)
            .setMessage(R.string.call_permission_message)
            .setPositiveButton(R.string.button_continue) { _, _ ->
                requestCallPermission.launch(Manifest.permission.CALL_PHONE)
            }
            .setNegativeButton(R.string.button_not_now, null)
            .show()
    }

    private fun showMessage(messageId: Int) {
        val root = binding?.root ?: return
        Snackbar.make(root, messageId, Snackbar.LENGTH_SHORT).show()
    }
}
