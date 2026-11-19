package cl.withyou.app.ui.contacts

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContract
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
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
import cl.withyou.app.ui.scrollTo
import cl.withyou.app.ui.showAppDialog
import cl.withyou.app.ui.security.SecurityViewModel
import cl.withyou.app.ui.security.withPin
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Pantalla Contactos (CU-07, mockup 5). Solo muestra y avisa: validar y guardar lo hace
 * ContactsViewModel, y llamar lo hace AssistantViewModel (pasando por Confirmar, RF-10).
 * Ver y llamar es libre; agregar, editar y eliminar pide el PIN del cuidador si existe (§8).
 */
class ContactsFragment : Fragment(R.layout.fragment_contacts) {

    private var binding: FragmentContactsBinding? = null
    private val viewModel: ContactsViewModel by viewModels { ContactsViewModel.Factory }
    private val assistant: AssistantViewModel by activityViewModels { AssistantViewModel.Factory }
    private val security: SecurityViewModel by activityViewModels { SecurityViewModel.Factory }

    private val adapter = ContactsAdapter(
        onEdit = { contact -> withPin(security) { showForm(contact) } },
        onCall = { assistant.requestCall(it) },
    )

    /**
     * Abre la agenda para elegir un número. Android da acceso solo al contacto elegido, así que no
     * se necesita el permiso READ_CONTACTS (RNF-06).
     */
    private val pickPhoneContact = registerForActivityResult(PickPhoneNumber()) { uri ->
        if (uri != null) importContact(uri)
    }

    /** Si se niega, igual se puede llamar: se abre el teléfono con el número marcado. */
    private val requestCallPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentContactsBinding.bind(view).also { binding = it }

        binding.contactsList.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsList.adapter = adapter
        binding.addContactButton.setOnClickListener { withPin(security) { showForm(null) } }
        binding.importContactButton.setOnClickListener { withPin(security) { openPhoneContacts() } }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.contacts.collect { contacts ->
                    adapter.submitList(contacts)
                    binding.emptyText.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
                    // Una lista vacía visible sería un elemento sin nombre para TalkBack (RNF-08).
                    binding.contactsList.visibility = if (contacts.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    /** Al salir de la pantalla se vuelve a bloquear con el PIN. */
    override fun onStop() {
        security.lock()
        super.onStop()
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

        requireContext().showAppDialog(
            title = getString(if (contact == null) R.string.contact_form_title_new else R.string.contact_form_title_edit),
            content = form.root,
            positiveText = getString(R.string.button_save),
            negativeText = getString(R.string.button_cancel),
            closeOnPositive = false,
            destructiveText = contact?.let { getString(R.string.button_delete) },
            onDestructive = { contact?.let(::confirmDelete) },
        ) { dialog ->
            val result = viewModel.save(contact?.id, form.nameInput.text.toString(), form.phoneInput.text.toString())
            form.nameLayout.error = if (result == ContactFormResult.NAME_INVALID) getString(R.string.error_name_missing) else null
            form.phoneLayout.error = if (result == ContactFormResult.PHONE_INVALID) getString(R.string.error_phone_invalid) else null
            if (result == ContactFormResult.SAVED) {
                dialog.dismiss()
                showMessage(R.string.contact_saved)
                offerCallPermission()
            } else {
                // El error debe verse: se desplaza hasta él y se pone el cursor en ese campo.
                val field = if (result == ContactFormResult.NAME_INVALID) form.nameLayout else form.phoneLayout
                field.editText?.requestFocus()
                dialog.scrollTo(field)
            }
        }
    }

    private fun openPhoneContacts() {
        try {
            pickPhoneContact.launch(Unit)
        } catch (e: ActivityNotFoundException) {
            showMessage(R.string.import_no_app)
        }
    }

    private fun importContact(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = viewModel.importFromPhone(uri.toString())) {
                is ContactImportResult.Imported -> {
                    showMessage(getString(R.string.import_done, result.name))
                    offerCallPermission()
                }
                is ContactImportResult.AlreadyExists -> showMessage(getString(R.string.import_exists, result.name))
                ContactImportResult.InvalidPhone -> showMessage(R.string.import_invalid_phone)
                ContactImportResult.InvalidName -> showMessage(R.string.import_invalid_name)
                ContactImportResult.Failed -> showMessage(R.string.import_failed)
            }
        }
    }

    private fun confirmDelete(contact: Contact) {
        requireContext().showAppDialog(
            title = getString(R.string.delete_contact_title, contact.name),
            message = getString(R.string.delete_contact_message),
            positiveText = getString(R.string.button_delete),
            negativeText = getString(R.string.button_cancel),
        ) {
            viewModel.delete(contact)
            showMessage(R.string.contact_deleted)
        }
    }

    /** Explica en lenguaje simple para qué es el permiso de llamadas antes de pedirlo (RNF-06). */
    private fun offerCallPermission() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        if (granted || !viewModel.shouldExplainCallPermission()) return
        requireContext().showAppDialog(
            title = getString(R.string.call_permission_title),
            message = getString(R.string.call_permission_message),
            positiveText = getString(R.string.button_continue),
            negativeText = getString(R.string.button_not_now),
        ) { requestCallPermission.launch(Manifest.permission.CALL_PHONE) }
    }

    private fun showMessage(messageId: Int) = showMessage(getString(messageId))

    private fun showMessage(message: String) {
        val root = binding?.root ?: return
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show()
    }

    /** Agenda del teléfono en modo "elegir un número": devuelve el número elegido o null. */
    private class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit) =
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            intent?.data.takeIf { resultCode == Activity.RESULT_OK }
    }
}
