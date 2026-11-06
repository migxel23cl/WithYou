package cl.withyou.app.ui.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cl.withyou.app.R
import cl.withyou.app.databinding.ItemContactBinding
import cl.withyou.app.domain.model.Contact

/** Fila de la lista: avatar con la inicial, nombre, número y botón verde de llamar (mockup 5). */
class ContactsAdapter(
    private val onEdit: (Contact) -> Unit,
    private val onCall: (Contact) -> Unit,
) : ListAdapter<Contact, ContactsAdapter.ContactViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder =
        ContactViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ContactViewHolder(private val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            val context = binding.root.context
            binding.avatar.text = contact.name.firstOrNull { it.isLetter() }?.uppercase().orEmpty()
            binding.nameText.text = contact.name
            binding.phoneText.text = contact.phone
            binding.contactCard.contentDescription =
                context.getString(R.string.edit_contact_description, contact.name, contact.phone)
            binding.callButton.contentDescription = context.getString(R.string.call_contact_description, contact.name)
            binding.contactCard.setOnClickListener { onEdit(contact) }
            binding.callButton.setOnClickListener { onCall(contact) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(oldItem: Contact, newItem: Contact) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Contact, newItem: Contact) = oldItem == newItem
        }
    }
}
