package com.expensetracker.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.expensetracker.CategoryColors
import com.expensetracker.R
import com.expensetracker.db.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(private var items: List<Transaction>) :
    RecyclerView.Adapter<TransactionAdapter.VH>() {

    private val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.dotCategory)
        val merchant: TextView = view.findViewById(R.id.tvMerchant)
        val categoryDate: TextView = view.findViewById(R.id.tvCategoryDate)
        val amount: TextView = view.findViewById(R.id.tvAmount)
    }

    fun submitList(newItems: List<Transaction>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val txn = items[position]
        holder.merchant.text = txn.merchant
        holder.categoryDate.text = "${txn.category} · ${dateFormat.format(Date(txn.timestamp))}"

        val color = CategoryColors.forCategory(txn.category)
        (holder.dot.background.mutate() as? GradientDrawable)?.setColor(color)

        val isDebit = txn.type == "DEBIT"
        holder.amount.text = (if (isDebit) "-₹" else "+₹") + "%,.0f".format(txn.amount)
        holder.amount.setTextColor(
            android.graphics.Color.parseColor(if (isDebit) "#F87171" else "#4ADE80")
        )
    }

    override fun getItemCount() = items.size
}
