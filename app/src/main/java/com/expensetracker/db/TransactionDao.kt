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

    /**
     * Monthly spend totals over a trailing window, used to compute how much
     * month-to-month variation there actually is instead of assuming a figure.
     */
    @Query("""
        SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS month,
               SUM(amount) AS total
        FROM transactions
        WHERE type = 'DEBIT' AND category != 'Investments' AND timestamp >= :since
        GROUP BY month
        ORDER BY month ASC
    """)
    suspend fun monthlyTotalsSince(since: Long): List<MonthlyTotal>

    /**
     * Same amount to the same merchant within a few minutes — almost always an
     * accidental double payment rather than two intentional ones.
     */
    @Query("""
        SELECT SUM(amount) FROM transactions t1
        WHERE t1.type = 'DEBIT'
          AND t1.timestamp BETWEEN :start AND :end
          AND EXISTS (
            SELECT 1 FROM transactions t2
            WHERE t2.id != t1.id
              AND t2.amount = t1.amount
              AND t2.merchant = t1.merchant
              AND t2.type = 'DEBIT'
              AND ABS(t2.timestamp - t1.timestamp) < 300000
              AND t2.id < t1.id
          )
    """)
    suspend fun duplicateSpendInRange(start: Long, end: Long): Double?

    /**
     * Strongest possible match for the one-time GPay export backfill: the UPI
     * transaction ID from the export appears verbatim inside the bank SMS text
     * ("...UPI Ref no 124035200941)..."), so searching for it directly is a
     * near-certain match — far more reliable than amount+time guessing, and used
     * as the first attempt before falling back to that.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE rawSms LIKE '%' || :upiTxnId || '%'
          AND merchantSource != 'ALIAS'
          AND rawMerchantKey LIKE 'Account XX%'
    """)
    suspend fun findByUpiRefInRawSms(upiTxnId: String): List<Transaction>

    /**
     * Fallback when the UPI ref isn't found verbatim (older SMS formats, OCR
     * gaps, etc.) — same amount+time window used by the notification matcher.
     * Only still-unresolved "Account XX...." entries are eligible, so this can
     * never touch anything already labeled by hand or by a live notification.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE type = :type
          AND merchantSource != 'ALIAS'
          AND rawMerchantKey LIKE 'Account XX%'
          AND amount BETWEEN :amountLow AND :amountHigh
          AND timestamp BETWEEN :windowStart AND :windowEnd
    """)
    suspend fun findByAmountTimeWindow(
        type: String,
        amountLow: Double,
        amountHigh: Double,
        windowStart: Long,
        windowEnd: Long
    ): List<Transaction>

    @Query("UPDATE transactions SET merchant = :merchant, category = :category, merchantSource = 'CSV_IMPORT' WHERE id = :id")
    suspend fun backfillFromCsv(id: Long, merchant: String, category: String)
}

data class CategoryTotal(val category: String, val total: Double, val count: Int)
data class MerchantTotal(val merchant: String, val total: Double, val count: Int)
data class MonthlyTotal(val month: String, val total: Double)
