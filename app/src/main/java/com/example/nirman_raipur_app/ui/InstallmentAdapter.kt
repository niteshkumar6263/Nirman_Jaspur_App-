package com.example.nirman_raipur_app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import com.example.nirman_raipur_app.R
import com.google.android.material.textfield.TextInputEditText

class InstallmentAdapter(
    initial: MutableList<Installment>,
    private val removeCallback: (Int) -> Unit,
    private val pickDateCallback: (Int) -> Unit
) : RecyclerView.Adapter<InstallmentAdapter.VH>() {

    val items = initial

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val etAmount: TextInputEditText = view.findViewById(R.id.etInstAmount)
        val tvDate: TextView = view.findViewById(R.id.tvInstDate)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveInst)

        fun bind(pos: Int) {
            val it = items[pos]
            etAmount.setText(it.amount)
            tvDate.text = if (it.date.isBlank()) "dd-mm-yyyy" else it.date

            btnRemove.setOnClickListener {
                removeAt(adapterPosition)
                removeCallback(adapterPosition)
            }

            tvDate.setOnClickListener {
                pickDateCallback(adapterPosition)
            }

            etAmount.addTextChangedListener { items[adapterPosition].amount = it?.toString() ?: "" }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_installment, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = items.size

    fun add(inst: Installment) {
        items.add(inst)
        notifyItemInserted(items.size - 1)
    }

    fun removeAt(pos: Int) {
        if (pos in items.indices) {
            items.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }

    fun replaceAll(list: MutableList<Installment>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}
