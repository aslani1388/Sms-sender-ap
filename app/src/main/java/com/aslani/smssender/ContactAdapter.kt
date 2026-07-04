package com.aslani.smssender

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(private val contacts: MutableList<Contact>) :
    RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvItemName)
        val phone: TextView = view.findViewById(R.id.tvItemPhone)
        val status: TextView = view.findViewById(R.id.tvItemStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val c = contacts[position]
        holder.name.text = c.name
        holder.phone.text = c.phone
        holder.status.text = c.status
    }

    override fun getItemCount(): Int = contacts.size

    fun updateStatus(position: Int, status: String) {
        contacts[position].status = status
        notifyItemChanged(position)
    }

    fun setContacts(newList: List<Contact>) {
        contacts.clear()
        contacts.addAll(newList)
        notifyDataSetChanged()
    }
}
