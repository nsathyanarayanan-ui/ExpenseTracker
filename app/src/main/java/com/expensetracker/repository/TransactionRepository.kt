package com.expensetracker.repository

import android.content.Context
import com.expensetracker.db.AppDatabase
import com.expensetracker.db.CategoryTotal
import com.expensetracker.db.MerchantTotal
import com.expensetracker.db.Transaction

class TransactionRepository(context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.transactionDao()
    private val budgetDao = db.budgetDao()

    suspend fun categoryBreakdown(start: Long, end: Long): List<CategoryTotal> =
        dao.categoryBreakdown(start, end)

    suspend fun merchantBreakdown(start: Long, end: Long): List<MerchantTotal> =
        dao.merchantBreakdown(start, end)

    suspend fun totalDebit(start: Long, end: Long): Double = dao.totalDebit(start, end) ?: 0.0
    suspend fun totalCredit(start: Long, end: Long): Double = dao.totalCredit(start, end) ?: 0.0

    suspend fun topExpenses(n: Int = 20): List<Transaction> = dao.topExpenses(n)

    suspend fun getRecentInRange(start: Long, end: Long, limit: Int = 30): List<Transaction> =
        dao.getRecentInRange(start, end, limit)

    suspend fun transactionsInCategory(category: String, start: Long, end: Long): List<Transaction> =
        dao.transactionsInCategory(category, start, end)

    suspend fun setBudget(category: String, limit: Double) =
        budgetDao.setBudget(com.expensetracker.db.Budget(category, limit))

    suspend fun getBudgets() = budgetDao.getAll()
}
