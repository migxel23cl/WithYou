package cl.withyou.app.ui.messages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cl.withyou.app.R
import cl.withyou.app.databinding.ItemRecipientBinding
import cl.withyou.app.domain.model.Contact

/** Contacto que se puede elegir como destinatario: queda marcado con un check (mockup 3). */
class RecipientAdapter(
    private val onSelect: (Contact) -> Unit,
) : ListAdapter<Contact, RecipientAdapter.RecipientViewHolder>(DIFF) {

    private var selectedId: Long? = null

    fun submit(contacts: List<Contact>, selected: Contact?) {
        val changed = selectedId != selected?.id
        selectedId = selected?.id
        submitList(contacts)
        if (changed) notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipientViewHolder =
        RecipientViewHolder(ItemRecipientBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RecipientViewHolder, position: Int) = holder.bind(getItem(position))

    inner class RecipientViewHolder(private val binding: ItemRecipientBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            val context = binding.root.context
            val selected = contact.id == selectedId
            binding.avatar.text = contact.name.firstOrNull { it.isLetter() }?.uppercase().orEmpty()
            binding.nameText.text = contact.name
            binding.checkIcon.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            binding.recipientCard.isChecked = selected
            binding.recipientCard.strokeColor =
                ContextCompat.getColor(context, if (selected) R.color.primary else R.color.card)
            binding.recipientCard.setCardBackgroundColor(
                ContextCompat.getColor(context, if (selected) R.color.card_accent else R.color.card),
            )
            binding.recipientCard.contentDescription = context.getString(
                if (selected) R.string.selected_contact_description else R.string.select_contact_description,
                contact.name,
            )
            binding.recipientCard.setOnClickListener { onSelect(contact) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(oldItem: Contact, newItem: Contact) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Contact, newItem: Contact) = oldItem == newItem
        }
    }
}
