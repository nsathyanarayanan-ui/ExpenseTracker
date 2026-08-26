package com.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.expensetracker.databinding.ActivityMainBinding
import com.expensetracker.viewmodel.MainViewModel
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.utils.ColorTemplate

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

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

        requestPermissionsIfNeeded()
        observeViewModel()
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
        viewModel.totalDebit.observe(this) {
            binding.tvTotalDebit.text = "₹%,.0f".format(it)
        }
        viewModel.totalCredit.observe(this) {
            binding.tvTotalCredit.text = "₹%,.0f".format(it)
        }
        viewModel.healthScore.observe(this) {
            binding.tvHealthScore.text = "$it / 100"
        }
        viewModel.healthBand.observe(this) {
            binding.tvHealthBand.text = "Rating: $it"
        }
        viewModel.categoryTotals.observe(this) { cats ->
            renderPieChart(cats)
        }
        viewModel.merchantTotals.observe(this) { merchants ->
            renderBarChart(merchants.take(10))
        }
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
                addLine(
                    binding.savingsContainer,
                    "• ${it.title} — save ₹%,.0f/mo (${it.basis})".format(it.monthlySavings),
                    "#5EEAD4"
                )
            }
        }
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
        val entries = cats.map { PieEntry(it.total.toFloat(), it.category) }
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList() + ColorTemplate.VORDIPLOM_COLORS.toList()
        dataSet.valueTextSize = 10f
        dataSet.valueTextColor = android.graphics.Color.WHITE

        binding.pieChart.data = PieData(dataSet)
        binding.pieChart.description.isEnabled = false
        binding.pieChart.legend.textColor = android.graphics.Color.WHITE
        binding.pieChart.legend.orientation = Legend.LegendOrientation.VERTICAL
        binding.pieChart.setEntryLabelColor(android.graphics.Color.WHITE)
        binding.pieChart.setHoleColor(android.graphics.Color.parseColor("#171A21"))
        binding.pieChart.animateY(600)
        binding.pieChart.invalidate()
    }

    private fun renderBarChart(merchants: List<com.expensetracker.db.MerchantTotal>) {
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
