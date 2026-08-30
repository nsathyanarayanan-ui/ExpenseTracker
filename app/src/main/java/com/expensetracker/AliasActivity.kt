package com.expensetracker

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.expensetracker.db.AppDatabase
import com.expensetracker.db.MerchantAlias
import kotlinx.coroutines.launch

class AliasActivity : AppCompatActivity() {

    private val categories = listOf(
        "Food Delivery", "Dining Out & Snacks", "Groceries", "Healthcare",
        "Utilities & Bills", "Fuel & Transport", "Education", "Entertainment",
        "Shopping", "Subscriptions", "Travel", "Personal Transfers", "Bank Transfer",
        "Other / Miscellaneous"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aliases)

        val rv = findViewById<RecyclerView>(R.id.rvAliases)
        rv.layoutManager = LinearLayoutManager(this)

        val db = AppDatabase.getInstance(this)

        lifecycleScope.launch {
            val rawKeys = db.merchantAliasDao().getUnlabeledAccountKeys()
            val existing = db.merchantAliasDao().getAll().associateBy { it.rawKey }

            rv.adapter = AliasAdapter(rawKeys, existing, categories) { rawKey, label, category ->
                lifecycleScope.launch {
                    db.merchantAliasDao().upsert(MerchantAlias(rawKey, label, category))
                    db.transactionDao().applyAliasToExisting(rawKey, label, category)
                }
            }
        }
    }
}

class AliasAdapter(
    private val rawKeys: List<String>,
    private val existing: Map<String, MerchantAlias>,
    private val categories: List<String>,
    private val onSave: (String, String, String) -> Unit
) : RecyclerView.Adapter<AliasAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
        val rawKeyText: TextView = view.findViewById(R.id.tvRawKey)
        val labelInput: EditText = view.findViewById(R.id.etLabel)
        val categorySpinner: Spinner = view.findViewById(R.id.spCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_alias, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rawKey = rawKeys[position]
        holder.rawKeyText.text = rawKey

        val adapter = ArrayAdapter(holder.view.context, android.R.layout.simple_spinner_dropdown_item, categories)
        holder.categorySpinner.adapter = adapter

        val current = existing[rawKey]
        holder.labelInput.setText(current?.label ?: "")
        current?.category?.let {
            val idx = categories.indexOf(it)
            if (idx >= 0) holder.categorySpinner.setSelection(idx)
        }

        fun trySave() {
            val label = holder.labelInput.text?.toString()?.trim().orEmpty()
            val category = categories.getOrNull(holder.categorySpinner.selectedItemPosition) ?: return
            if (label.isNotEmpty()) onSave(rawKey, label, category)
        }

        holder.labelInput.removeTextChangedListener(holder.view.getTag(R.id.tvRawKey) as? TextWatcher)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { trySave() }
        }
        holder.labelInput.addTextChangedListener(watcher)
        holder.view.setTag(R.id.tvRawKey, watcher)

        holder.categorySpinner.post {
            holder.categorySpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { trySave() }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            })
        }
    }

    override fun getItemCount() = rawKeys.size
}
