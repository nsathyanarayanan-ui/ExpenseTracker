package com.expensetracker

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.expensetracker.db.AppDatabase
import com.expensetracker.db.Budget
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private val categories = listOf(
        "Food Delivery", "Dining Out & Snacks", "Groceries", "Healthcare",
        "Utilities & Bills", "Fuel & Transport", "Education", "Entertainment",
        "Shopping", "Subscriptions", "Travel", "Personal Transfers", "Other / Miscellaneous"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val rv = findViewById<RecyclerView>(R.id.rvBudgets)
        rv.layoutManager = LinearLayoutManager(this)

        val db = AppDatabase.getInstance(this)

        lifecycleScope.launch {
            val existing = db.budgetDao().getAll().associateBy { it.category }
            rv.adapter = BudgetAdapter(categories, existing) { category, limit ->
                lifecycleScope.launch {
                    db.budgetDao().setBudget(Budget(category, limit))
                }
            }
        }
    }
}

class BudgetAdapter(
    private val categories: List<String>,
    private val existing: Map<String, Budget>,
    private val onSave: (String, Double) -> Unit
) : RecyclerView.Adapter<BudgetAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvCategoryName)
        val limit: EditText = view.findViewById(R.id.etLimit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_budget, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val category = categories[position]
        holder.name.text = category
        val current = existing[category]?.monthlyLimit
        holder.limit.setText(if (current != null) current.toInt().toString() else "")

        holder.limit.removeTextChangedListener(holder.view.getTag(R.id.tvCategoryName) as? TextWatcher)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toDoubleOrNull() ?: return
                onSave(category, value)
            }
        }
        holder.limit.addTextChangedListener(watcher)
        holder.view.setTag(R.id.tvCategoryName, watcher)
    }

    override fun getItemCount() = categories.size
}
