package com.expensetracker.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction): Long

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getInRange(start: Long, end: Long): List<Transaction>

    @Query("""
        SELECT category AS category, SUM(amount) AS total, COUNT(*) AS count
        FROM transactions
        WHERE type = 'DEBIT' AND category != 'Investments' AND timestamp BETWEEN :start AND :end
        GROUP BY category ORDER BY total DESC
    """)
    suspend fun categoryBreakdown(start: Long, end: Long): List<CategoryTotal>

    @Query("""
        SELECT merchant AS merchant, SUM(amount) AS total, COUNT(*) AS count
        FROM transactions
        WHERE type = 'DEBIT' AND category != 'Investments' AND timestamp BETWEEN :start AND :end
        GROUP BY merchant ORDER BY total DESC
    """)
    suspend fun merchantBreakdown(start: Long, end: Long): List<MerchantTotal>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND category != 'Investments' AND timestamp BETWEEN :start AND :end")
    suspend fun totalDebit(start: Long, end: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND category = 'Investments' AND timestamp BETWEEN :start AND :end")
    suspend fun totalInvested(start: Long, end: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CREDIT' AND timestamp BETWEEN :start AND :end")
    suspend fun totalCredit(start: Long, end: Long): Double?

    @Query("""
        SELECT SUM(amount) FROM transactions
        WHERE type = 'DEBIT' AND category = :category AND timestamp BETWEEN :start AND :end
    """)
    suspend fun categoryTotal(category: String, start: Long, end: Long): Double?

    @Query("SELECT * FROM transactions WHERE category = :category AND timestamp BETWEEN :start AND :end ORDER BY amount DESC")
    suspend fun transactionsInCategory(category: String, start: Long, end: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentInRange(start: Long, end: Long, limit: Int): List<Transaction>

    @Query("SELECT * FROM transactions WHERE type = 'DEBIT' ORDER BY amount DESC LIMIT :n")
    suspend fun topExpenses(n: Int): List<Transaction>

    @Query("UPDATE transactions SET merchant = :label, category = :category WHERE rawMerchantKey = :rawKey")
    suspend fun applyAliasToExisting(rawKey: String, label: String, category: String)

    @Query("UPDATE transactions SET merchant = :merchant, category = :category WHERE timestamp = :timestamp AND rawSms = :rawSms")
    suspend fun recategorizeExisting(timestamp: Long, rawSms: String, merchant: String, category: String)

    /**
     * Find a transaction that a notification could enrich: same amount, close in time,
     * and whose merchant hasn't already been set by a notification or a manual alias.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE amount = :amount
          AND type = 'DEBIT'
          AND merchantSource = 'SMS'
          AND timestamp BETWEEN :windowStart AND :windowEnd
        ORDER BY ABS(timestamp - :targetTime) ASC
        LIMIT 1
    """)
    suspend fun findEnrichableTransaction(amount: Double, windowStart: Long, windowEnd: Long, targetTime: Long): Transaction?

    @Query("UPDATE transactions SET merchant = :merchant, category = :category, merchantSource = 'NOTIFICATION' WHERE id = :id")
    suspend fun enrichWithNotification(id: Long, merchant: String, category: String)
}

data class CategoryTotal(val category: String, val total: Double, val count: Int)
data class MerchantTotal(val merchant: String, val total: Double, val count: Int)
