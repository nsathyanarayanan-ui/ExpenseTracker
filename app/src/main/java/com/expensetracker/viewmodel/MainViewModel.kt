package com.expensetracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.expensetracker.InsightsEngine
import com.expensetracker.SavingsOpportunity
import com.expensetracker.db.CategoryTotal
import com.expensetracker.db.MerchantTotal
import com.expensetracker.db.Transaction
import com.expensetracker.repository.TransactionRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TransactionRepository(app)

    val totalDebit = MutableLiveData(0.0)
    val totalCredit = MutableLiveData(0.0)
    val categoryTotals = MutableLiveData<List<CategoryTotal>>(emptyList())
    val merchantTotals = MutableLiveData<List<MerchantTotal>>(emptyList())
    val recentTransactions = MutableLiveData<List<Transaction>>(emptyList())
    val unnecessaryFlags = MutableLiveData<List<String>>(emptyList())
    val savingsOpportunities = MutableLiveData<List<SavingsOpportunity>>(emptyList())
    val healthScore = MutableLiveData(0)
    val healthBand = MutableLiveData("")
    val monthLabel = MutableLiveData("")
    val canGoForward = MutableLiveData(false)

    private var monthOffset = 0
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun loadCurrentMonth() = loadMonth(0)

    fun goToPreviousMonth() = loadMonth(monthOffset - 1)
    fun goToNextMonth() {
        if (monthOffset < 0) loadMonth(monthOffset + 1)
    }

    private fun loadMonth(offset: Int) {
        monthOffset = offset
        canGoForward.value = monthOffset < 0

        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, offset)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val start = cal.timeInMillis
        monthLabel.value = monthFormat.format(cal.time)

        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis - 1

        load(start, end)
    }

    private fun load(start: Long, end: Long) {
        viewModelScope.launch {
            val debit = repo.totalDebit(start, end)
            val credit = repo.totalCredit(start, end)
            val cats = repo.categoryBreakdown(start, end)
            val merchants = repo.merchantBreakdown(start, end)
            val recent = repo.getRecentInRange(start, end, 30)

            totalDebit.value = debit
            totalCredit.value = credit
            categoryTotals.value = cats
            merchantTotals.value = merchants
            recentTransactions.value = recent

            unnecessaryFlags.value = InsightsEngine.unnecessarySpendFlags(cats, merchants, debit)
            val savings = InsightsEngine.savingsOpportunities(cats, merchants)
            savingsOpportunities.value = savings

            val discretionaryRatio = cats.filter {
                it.category in com.expensetracker.Categorizer.DISCRETIONARY_CATEGORIES
            }.sumOf { it.total }.let { if (debit > 0) it / debit else 0.0 }

            val reclaimablePct = if (debit > 0) savings.sumOf { it.monthlySavings } / debit else 0.0

            val score = InsightsEngine.healthScore(
                discretionaryRatio = discretionaryRatio,
                monthlyCoefficientOfVariation = 0.15,
                anomalyRatio = 0.0,
                reclaimablePct = reclaimablePct
            )
            healthScore.value = score.total
            healthBand.value = score.band
        }
    }
}
