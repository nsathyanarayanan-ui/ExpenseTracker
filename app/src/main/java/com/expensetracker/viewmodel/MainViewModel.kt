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

data class BudgetStatusLine(val text: String, val colorHex: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TransactionRepository(app)

    val totalDebit = MutableLiveData(0.0)
    val totalCredit = MutableLiveData(0.0)
    val totalInvested = MutableLiveData(0.0)
    val categoryTotals = MutableLiveData<List<CategoryTotal>>(emptyList())
    val merchantTotals = MutableLiveData<List<MerchantTotal>>(emptyList())
    val recentTransactions = MutableLiveData<List<Transaction>>(emptyList())
    val unnecessaryFlags = MutableLiveData<List<String>>(emptyList())
    val savingsOpportunities = MutableLiveData<List<SavingsOpportunity>>(emptyList())
    val healthScore = MutableLiveData(0)
    val healthBand = MutableLiveData("")
    val healthBreakdown = MutableLiveData<List<BudgetStatusLine>>(emptyList())
    val monthLabel = MutableLiveData("")
    val canGoForward = MutableLiveData(false)
    val budgetStatus = MutableLiveData<List<BudgetStatusLine>>(emptyList())

    private var monthOffset = 0
    private var rangeStart = 0L
    private var rangeEnd = 0L
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

        rangeStart = start
        rangeEnd = end
        load(start, end)
    }

    /** Call after editing a budget in Settings to refresh the status list without a full reload. */
    fun refreshBudgetStatus() {
        viewModelScope.launch {
            val cats = categoryTotals.value ?: emptyList()
            budgetStatus.value = computeBudgetStatus(cats, rangeStart, rangeEnd)
        }
    }

    private suspend fun computeBudgetStatus(cats: List<CategoryTotal>, start: Long, end: Long): List<BudgetStatusLine> {
        val budgets = repo.getBudgets()
        if (budgets.isEmpty()) return emptyList()
        val spentByCategory = cats.associateBy { it.category }

        return budgets.map { budget ->
            val spent = spentByCategory[budget.category]?.total ?: 0.0
            val pct = if (budget.monthlyLimit > 0) (spent / budget.monthlyLimit * 100) else 0.0
            val spentStr = "₹%,.0f".format(spent)
            val limitStr = "₹%,.0f".format(budget.monthlyLimit)
            val pctStr = "%.0f".format(pct)
            val color = when {
                pct >= 100 -> "#F87171"
                pct >= 80 -> "#FBBF24"
                else -> "#4ADE80"
            }
            BudgetStatusLine("${budget.category}: $spentStr / $limitStr ($pctStr%)", color)
        }
    }

    private fun load(start: Long, end: Long) {
        viewModelScope.launch {
            val debit = repo.totalDebit(start, end)
            val invested = repo.totalInvested(start, end)
            val credit = repo.totalCredit(start, end)
            val cats = repo.categoryBreakdown(start, end)
            val merchants = repo.merchantBreakdown(start, end)
            val recent = repo.getRecentInRange(start, end, 30)

            totalDebit.value = debit
            totalInvested.value = invested
            totalCredit.value = credit
            categoryTotals.value = cats
            merchantTotals.value = merchants
            recentTransactions.value = recent
            budgetStatus.value = computeBudgetStatus(cats, start, end)

            unnecessaryFlags.value = InsightsEngine.unnecessarySpendFlags(cats, merchants, debit)
            val savings = InsightsEngine.savingsOpportunities(cats, merchants)
            savingsOpportunities.value = savings

            val discretionaryRatio = cats.filter {
                it.category in com.expensetracker.Categorizer.DISCRETIONARY_CATEGORIES
            }.sumOf { it.total }.let { if (debit > 0) it / debit else 0.0 }

            val reclaimablePct = if (debit > 0) savings.sumOf { it.monthlySavings } / debit else 0.0

            // Month-to-month variability, measured rather than assumed. Needs at least
            // two complete months of history; until then, a neutral mid-range value is
            // used so a brand-new install isn't scored as wildly erratic.
            val sixMonthsAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.timeInMillis
            val monthly = repo.monthlyTotalsSince(sixMonthsAgo).map { it.total }
            val coefficientOfVariation = if (monthly.size >= 2) {
                val mean = monthly.average()
                if (mean > 0) {
                    val variance = monthly.sumOf { (it - mean) * (it - mean) } / monthly.size
                    kotlin.math.sqrt(variance) / mean
                } else 0.0
            } else 0.15

            // Share of this month's spend that looks like accidental double payments.
            val duplicateSpend = repo.duplicateSpendInRange(start, end)
            val anomalyRatio = if (debit > 0) duplicateSpend / debit else 0.0

            val score = InsightsEngine.healthScore(
                discretionaryRatio = discretionaryRatio,
                monthlyCoefficientOfVariation = coefficientOfVariation,
                anomalyRatio = anomalyRatio,
                reclaimablePct = reclaimablePct
            )
            healthScore.value = score.total
            healthBand.value = score.band

            fun bandColor(actual: Int, max: Int): String {
                val pct = if (max > 0) actual.toDouble() / max else 0.0
                return when {
                    pct >= 0.75 -> "#4ADE80"
                    pct >= 0.45 -> "#FBBF24"
                    else -> "#F87171"
                }
            }

            healthBreakdown.value = listOf(
                BudgetStatusLine(
                    "Discretionary spend  ${score.discretionaryScore}/30  (${"%.0f".format(discretionaryRatio * 100)}% of outgoings)",
                    bandColor(score.discretionaryScore, 30)
                ),
                BudgetStatusLine(
                    "Month-to-month consistency  ${score.consistencyScore}/25  (variation ${"%.0f".format(coefficientOfVariation * 100)}%)",
                    bandColor(score.consistencyScore, 25)
                ),
                BudgetStatusLine(
                    "Duplicate/anomaly control  ${score.anomalyScore}/20" +
                        if (duplicateSpend > 0) "  (₹${"%,.0f".format(duplicateSpend)} looks duplicated)" else "",
                    bandColor(score.anomalyScore, 20)
                ),
                BudgetStatusLine(
                    "Savings capacity  ${score.savingsCapacityScore}/25  (${"%.0f".format(reclaimablePct * 100)}% reclaimable)",
                    bandColor(score.savingsCapacityScore, 25)
                )
            )
        }
    }
}
