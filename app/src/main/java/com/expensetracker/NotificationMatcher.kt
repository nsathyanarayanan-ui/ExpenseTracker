package com.expensetracker

import android.content.Context
import com.expensetracker.db.AppDatabase
import com.expensetracker.db.PendingNotification

/**
 * Pairs UPI app notifications (which know the merchant) with bank SMS transactions
 * (which are the authoritative record of the money moving).
 *
 * Order of arrival isn't guaranteed, so matching runs in both directions:
 *  - notification arrives -> look for a recent unenriched transaction
 *  - SMS arrives          -> look back for a recent unconsumed notification
 *
 * A match requires an exact amount match plus proximity in time. Amount alone is
 * too weak (two ₹100 payments in a day are common); the time window is what makes
 * it safe. Manual aliases always win — this never overwrites a label the user set.
 */
object NotificationMatcher {

    /** Bank SMS typically lands within seconds of the payment app's notification. */
    private const val MATCH_WINDOW_MS = 3 * 60 * 1000L      // ±3 minutes

    /** Unmatched notifications past this age are dropped as noise. */
    private const val RETENTION_MS = 24 * 60 * 60 * 1000L   // 24 hours

    /**
     * Called when a UPI notification is captured. Enriches an existing transaction
     * if one is already waiting; otherwise parks the merchant name for the SMS
     * that's about to arrive.
     */
    suspend fun onNotification(context: Context, parsed: ParsedNotification) {
        val db = AppDatabase.getInstance(context)
        val txnDao = db.transactionDao()
        val notifDao = db.pendingNotificationDao()

        // Housekeeping first so the table doesn't grow without bound.
        notifDao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MS)

        val existing = txnDao.findEnrichableTransaction(
            amount = parsed.amount,
            windowStart = parsed.timestamp - MATCH_WINDOW_MS,
            windowEnd = parsed.timestamp + MATCH_WINDOW_MS,
            targetTime = parsed.timestamp
        )

        if (existing != null) {
            val category = Categorizer.categorize(parsed.merchant, existing.rawSms)
            txnDao.enrichWithNotification(existing.id, parsed.merchant, category)
        } else {
            notifDao.insert(
                PendingNotification(
                    amount = parsed.amount,
                    merchant = parsed.merchant,
                    timestamp = parsed.timestamp
                )
            )
        }
    }

    /**
     * Called after a bank SMS transaction is stored. Returns the enriched merchant
     * name if a waiting notification matched, or null if none did.
     */
    suspend fun onTransactionStored(
        context: Context,
        transactionId: Long,
        amount: Double,
        timestamp: Long,
        rawSms: String
    ): String? {
        val db = AppDatabase.getInstance(context)
        val txnDao = db.transactionDao()
        val notifDao = db.pendingNotificationDao()

        val match = notifDao.findMatch(
            amount = amount,
            windowStart = timestamp - MATCH_WINDOW_MS,
            windowEnd = timestamp + MATCH_WINDOW_MS,
            targetTime = timestamp
        ) ?: return null

        val category = Categorizer.categorize(match.merchant, rawSms)
        txnDao.enrichWithNotification(transactionId, match.merchant, category)
        notifDao.markConsumed(match.id)
        return match.merchant
    }
}
