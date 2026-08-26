package com.expensetracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.expensetracker.InsightsEngine
import com.expensetracker.SavingsOpportunity
import com.expensetracker.db.CategoryTotal
import com.expensetracker.db.MerchantTotal
import com.expensetracker.repository.TransactionRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.pow
import kotlin.math.sqrt

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TransactionRepository(app)

    val totalDebit = MutableLiveData(0.0)
    val totalCredit = MutableLiveData(0.0)
    val categoryTotals = MutableLiveData<List<CategoryTotal>>(emptyList())
    val merchantTotals = MutableLiveData<List<MerchantTotal>>(emptyList())
    val unnecessaryFlags = MutableLiveData<List<String>>(emptyList())
    val savingsOpportunities = MutableLiveData<List<SavingsOpportunity>>(emptyList())
    val healthScore = MutableLiveData(0)
    val healthBand = MutableLiveData("")

    fun loadCurrentMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        load(start, end)
    }

    fun loadLastNMonths(n: Int) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -n)
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        load(start, end)
    }

    private fun load(start: Long, end: Long) {
        viewModelScope.launch {
            val debit = repo.totalDebit(start, end)
            val credit = repo.totalCredit(start, end)
            val cats = repo.categoryBreakdown(start, end)
            val merchants = repo.merchantBreakdown(start, end)

            totalDebit.value = debit
            totalCredit.value = credit
            categoryTotals.value = cats
            merchantTotals.value = merchants

            unnecessaryFlags.value = InsightsEngine.unnecessarySpendFlags(cats, merchants, debit)
            val savings = InsightsEngine.savingsOpportunities(cats, merchants)
            savingsOpportunities.value = savings

            // simple month-to-month consistency proxy using per-week totals within range
            val discretionaryRatio = cats.filter {
                it.category in com.expensetracker.Categorizer.DISCRETIONARY_CATEGORIES
            }.sumOf { it.total }.let { if (debit > 0) it / debit else 0.0 }

            val reclaimablePct = if (debit > 0) savings.sumOf { it.monthlySavings } / debit else 0.0

            val score = InsightsEngine.healthScore(
                discretionaryRatio = discretionaryRatio,
                monthlyCoefficientOfVariation = 0.15, // placeholder until 3+ months of history exist
                anomalyRatio = 0.0,
                reclaimablePct = reclaimablePct
            )
            healthScore.value = score.total
            healthBand.value = score.band
        }
    }
}
