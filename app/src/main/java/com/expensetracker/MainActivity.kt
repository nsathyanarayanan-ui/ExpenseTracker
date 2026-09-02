package com.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.expensetracker.adapter.TransactionAdapter
import com.expensetracker.databinding.ActivityMainBinding
import com.expensetracker.viewmodel.MainViewModel
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.components.Legend
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var transactionAdapter: TransactionAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.loadCurrentMonth()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        transactionAdapter = TransactionAdapter(emptyList())
        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = transactionAdapter

        requestPermissionsIfNeeded()
        observeViewModel()
        wireButtons()
        setupTabs()
    }

    private fun setupTabs() {
        val tabs = listOf(binding.tabOverview, binding.tabCategories, binding.tabActivity, binding.tabInsights)

        fun showTab(index: Int) {
            tabs.forEachIndexed { i, view -> view.visibility = if (i == index) View.VISIBLE else View.GONE }
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showTab(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        updateNotificationAccessCard()
    }

    private fun updateNotificationAccessCard() {
        val enabled = isNotificationAccessGranted()
        binding.cardNotificationAccess.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun isNotificationAccessGranted(): Boolean {
        val flat = android.provider.Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(packageName)
    }

    private fun wireButtons() {
        binding.btnPrevMonth.setOnClickListener { viewModel.goToPreviousMonth() }
        binding.btnNextMonth.setOnClickListener { viewModel.goToNextMonth() }

        binding.btnSettingsIcon.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
        binding.btnShareIcon.setOnClickListener { shareMonthlyReport() }
        binding.btnLabelIcon.setOnClickListener {
            startActivity(android.content.Intent(this, AliasActivity::class.java))
        }
        binding.btnLabelAccounts.setOnClickListener {
            startActivity(android.content.Intent(this, AliasActivity::class.java))
        }

        binding.btnEnableNotificationAccess.setOnClickListener {
            startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        binding.btnImportSms.setOnClickListener {
            binding.btnImportSms.isEnabled = false
            binding.btnImportSms.text = "Importing..."
            lifecycleScope.launch {
                val result = SmsImporter.importExisting(this@MainActivity)
                binding.btnImportSms.isEnabled = true
                binding.btnImportSms.text = "Import Past SMS"
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "Scanned ${result.scanned} messages, imported ${result.imported} transactions",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                viewModel.loadCurrentMonth()
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val notGranted = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isEmpty()) {
            viewModel.loadCurrentMonth()
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    private fun observeViewModel() {
        viewModel.monthLabel.observe(this) { binding.tvPeriod.text = it }
        viewModel.totalDebit.observe(this) { binding.tvTotalDebit.text = "₹%,.0f".format(it) }
        viewModel.totalCredit.observe(this) { binding.tvTotalCredit.text = "₹%,.0f".format(it) }
        viewModel.totalInvested.observe(this) { binding.tvTotalInvested.text = "₹%,.0f".format(it) }
        viewModel.healthScore.observe(this) { binding.tvHealthScore.text = "$it / 100" }
        viewModel.healthBand.observe(this) { binding.tvHealthBand.text = it }
        viewModel.categoryTotals.observe(this) { renderPieChart(it) }
        viewModel.merchantTotals.observe(this) { renderBarChart(it.take(10)) }
        viewModel.recentTransactions.observe(this) { transactionAdapter.submitList(it) }

        viewModel.unnecessaryFlags.observe(this) { flags ->
            binding.flagsContainer.removeAllViews()
            if (flags.isEmpty()) {
                addLine(binding.flagsContainer, "No major flags this period.", "#4ADE80")
            }
            flags.forEach { addLine(binding.flagsContainer, "• $it", "#F87171") }
        }
        viewModel.savingsOpportunities.observe(this) { savings ->
            binding.savingsContainer.removeAllViews()
            savings.forEach {
                val amountStr = "₹%,.0f".format(it.monthlySavings)
                val line = "• ${it.title} — save $amountStr/mo (${it.basis})"
                addLine(binding.savingsContainer, line, "#5EEAD4")
            }
        }
        viewModel.budgetStatus.observe(this) { lines ->
            binding.budgetStatusContainer.removeAllViews()
            if (lines.isNotEmpty()) {
                val header = TextView(this)
                header.text = "Budget Status"
                header.setTextColor(android.graphics.Color.WHITE)
                header.textSize = 15f
                header.setTypeface(header.typeface, android.graphics.Typeface.BOLD)
                header.setPadding(0, 4, 0, 8)
                binding.budgetStatusContainer.addView(header)
                lines.forEach { addLine(binding.budgetStatusContainer, it.text, it.colorHex) }
            }
        }
    }

    private fun shareMonthlyReport() {
        val month = binding.tvPeriod.text.toString()
        val spent = binding.tvTotalDebit.text.toString()
        val received = binding.tvTotalCredit.text.toString()
        val invested = binding.tvTotalInvested.text.toString()
        val health = binding.tvHealthScore.text.toString()
        val band = binding.tvHealthBand.text.toString()

        val cats = viewModel.categoryTotals.value ?: emptyList()
        val catLines = cats.joinToString("\n") {
            val totalStr = "₹%,.0f".format(it.total)
            "  ${it.category}: $totalStr (${it.count} txns)"
        }

        val savings = viewModel.savingsOpportunities.value ?: emptyList()
        val savingsLines = savings.joinToString("\n") {
            val amountStr = "₹%,.0f".format(it.monthlySavings)
            "  • ${it.title} — save $amountStr/mo"
        }

        val report = buildString {
            appendLine("Expense Tracker — $month")
            appendLine("=".repeat(32))
            appendLine("Spent: $spent")
            appendLine("Received: $received")
            appendLine("Invested: $invested")
            appendLine("Financial Health: $health ($band)")
            appendLine()
            appendLine("Spending by category:")
            appendLine(catLines.ifBlank { "  No transactions this period" })
            if (savings.isNotEmpty()) {
                appendLine()
                appendLine("Savings opportunities:")
                appendLine(savingsLines)
            }
        }

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Expense Tracker Report — $month")
            putExtra(android.content.Intent.EXTRA_TEXT, report)
        }
        startActivity(android.content.Intent.createChooser(intent, "Share report"))
    }

    private fun addLine(container: android.widget.LinearLayout, text: String, colorHex: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(android.graphics.Color.parseColor(colorHex))
        tv.textSize = 13f
        tv.setPadding(0, 6, 0, 6)
        container.addView(tv)
    }

    private fun renderPieChart(cats: List<com.expensetracker.db.CategoryTotal>) {
        if (cats.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.invalidate()
            return
        }
        val entries = cats.map { PieEntry(it.total.toFloat(), it.category) }
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = cats.map { CategoryColors.forCategory(it.category) }
        dataSet.valueTextSize = 10f
        dataSet.valueTextColor = android.graphics.Color.WHITE
        dataSet.sliceSpace = 2f

        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.description.isEnabled = false
        binding.pieChart.legend.textColor = android.graphics.Color.WHITE
        binding.pieChart.legend.orientation = Legend.LegendOrientation.VERTICAL
        binding.pieChart.setEntryLabelColor(android.graphics.Color.WHITE)
        binding.pieChart.setHoleColor(android.graphics.Color.parseColor("#161A22"))
        binding.pieChart.holeRadius = 45f
        binding.pieChart.animateY(600)
        binding.pieChart.invalidate()
    }

    private fun renderBarChart(merchants: List<com.expensetracker.db.MerchantTotal>) {
        if (merchants.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.invalidate()
            return
        }
        val entries = merchants.mapIndexed { i, m -> BarEntry(i.toFloat(), m.total.toFloat()) }
        val dataSet = BarDataSet(entries, "Spend")
        dataSet.color = android.graphics.Color.parseColor("#5EEAD4")
        dataSet.valueTextColor = android.graphics.Color.WHITE

        binding.barChart.data = BarData(dataSet)
        binding.barChart.description.isEnabled = false
        binding.barChart.legend.isEnabled = false
        binding.barChart.xAxis.textColor = android.graphics.Color.WHITE
        binding.barChart.axisLeft.textColor = android.graphics.Color.WHITE
        binding.barChart.axisRight.isEnabled = false
        binding.barChart.animateY(600)
        binding.barChart.invalidate()
    }
}
