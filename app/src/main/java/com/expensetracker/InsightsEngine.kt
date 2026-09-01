package com.expensetracker

import com.expensetracker.db.CategoryTotal
import com.expensetracker.db.MerchantTotal
import kotlin.math.max
import kotlin.math.min

data class SavingsOpportunity(val title: String, val basis: String, val monthlySavings: Double)

data class HealthScore(
    val total: Int,
    val discretionaryScore: Int,
    val consistencyScore: Int,
    val anomalyScore: Int,
    val savingsCapacityScore: Int,
    val band: String
)

object InsightsEngine {

    /** Flags categories/merchants worth reviewing based on spend share and frequency. */
    fun unnecessarySpendFlags(
        categories: List<CategoryTotal>,
        merchants: List<MerchantTotal>,
        totalSpend: Double
    ): List<String> {
        val flags = mutableListOf<String>()
        for (c in categories) {
            if (c.category in Categorizer.DISCRETIONARY_CATEGORIES && totalSpend > 0) {
                val pct = c.total / totalSpend * 100
                if (pct >= 12) {
                    flags.add("${c.category}: ₹%.0f (%.1f%% of spend) across %d transactions — high discretionary share"
                        .format(c.total, pct, c.count))
                }
            }
        }
        for (m in merchants) {
            if (m.count >= 15 && (m.total / m.count) < 1000) {
                flags.add("${m.merchant}: ${m.count} visits averaging ₹%.0f — frequent small-ticket spending, consider batching"
                    .format(m.total / m.count))
            }
        }
        return flags
    }

    /** Computes exact-rupee savings opportunities from real category/merchant totals. */
    fun savingsOpportunities(
        categories: List<CategoryTotal>,
        merchants: List<MerchantTotal>
    ): List<SavingsOpportunity> {
        val byCat = categories.associateBy { it.category }
        val result = mutableListOf<SavingsOpportunity>()

        byCat["Dining Out & Snacks"]?.let {
            result.add(SavingsOpportunity("Cut dining out by 30%", "30%% of ₹%.0f".format(it.total), it.total * 0.30))
        }
        byCat["Food Delivery"]?.let {
            result.add(SavingsOpportunity("Cut food delivery by 25%", "25%% of ₹%.0f".format(it.total), it.total * 0.25))
        }
        byCat["Entertainment"]?.let {
            result.add(SavingsOpportunity("Trim entertainment by 20%", "20%% of ₹%.0f".format(it.total), it.total * 0.20))
        }
        byCat["Subscriptions"]?.let {
            result.add(SavingsOpportunity("Audit idle subscriptions", "30%% of ₹%.0f if underused".format(it.total), it.total * 0.30))
        }
        val freqSmall = merchants.filter { it.count >= 15 && (it.total / it.count) < 1000 }
        if (freqSmall.isNotEmpty()) {
            val sum = freqSmall.sumOf { it.total }
            result.add(SavingsOpportunity("Batch frequent small-ticket shopping", "15%% of ₹%.0f across %d merchants".format(sum, freqSmall.size), sum * 0.15))
        }
        return result
    }

    /**
     * Composite 0-100 score across 4 weighted, data-derived components:
     * - Discretionary spend control (30 pts)
     * - Month-to-month consistency (25 pts) [caller passes stdev/mean of monthly spend]
     * - Anomaly control / duplicate charges (20 pts)
     * - Reclaimable savings capacity (25 pts)
     */
    fun healthScore(
        discretionaryRatio: Double,     // 0..1
        monthlyCoefficientOfVariation: Double, // stdev/mean, 0..1+
        anomalyRatio: Double,           // duplicate$/total$, 0..1
        reclaimablePct: Double          // savings potential / total spend, 0..1
    ): HealthScore {
        val discretionaryScore = (30 - max(0.0, (discretionaryRatio * 100 - 20)) * 1.2)
            .coerceIn(0.0, 30.0).toInt()
        val consistencyScore = (25 - monthlyCoefficientOfVariation * 100 * 0.7)
            .coerceIn(0.0, 25.0).toInt()
        val anomalyScore = (20 - anomalyRatio * 100 * 10).coerceIn(0.0, 20.0).toInt()
        val savingsCapacityScore = (25 - max(0.0, (reclaimablePct * 100 - 5)) * 1.5)
            .coerceIn(0.0, 25.0).toInt()

        val total = discretionaryScore + consistencyScore + anomalyScore + savingsCapacityScore
        val band = when {
            total >= 85 -> "Excellent"
            total >= 70 -> "Good"
            total >= 50 -> "Fair"
            else -> "Needs Attention"
        }
        return HealthScore(total, discretionaryScore, consistencyScore, anomalyScore, savingsCapacityScore, band)
    }
}
