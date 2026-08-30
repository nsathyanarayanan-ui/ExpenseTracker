package com.expensetracker

import android.graphics.Color

/** Single source of truth for category colors, so the pie chart, transaction
 *  list dots, and any future screens all agree visually on what each
 *  category looks like. */
object CategoryColors {
    private val MAP = mapOf(
        "Food Delivery" to "#FB923C",
        "Dining Out & Snacks" to "#F87171",
        "Groceries" to "#FBBF24",
        "Healthcare" to "#34D399",
        "Utilities & Bills" to "#60A5FA",
        "Fuel & Transport" to "#38BDF8",
        "Education" to "#A78BFA",
        "Education (Dance Classes)" to "#A78BFA",
        "Education (Online Courses)" to "#C084FC",
        "Entertainment" to "#F472B6",
        "Shopping" to "#E879F9",
        "Subscriptions" to "#4ADE80",
        "Travel" to "#22D3EE",
        "Personal Transfers" to "#94A3B8",
        "Bank Transfer" to "#64748B",
        "Other / Miscellaneous" to "#9CA3AF"
    )
    private const val DEFAULT = "#5EEAD4"

    fun forCategory(category: String): Int = Color.parseColor(MAP[category] ?: DEFAULT)
    fun hexForCategory(category: String): String = MAP[category] ?: DEFAULT
}
