package com.expensetracker.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A merchant name captured from a UPI app notification, held until the matching
 * bank SMS arrives (or matched immediately against one that already arrived).
 *
 * Notifications and bank SMS for the same payment don't arrive in a guaranteed
 * order and can be seconds apart, so both directions need to work: notification
 * first (wait for SMS) and SMS first (look back at recent notifications).
 */
@Entity(tableName = "pending_notifications")
data class PendingNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val timestamp: Long,
    val consumed: Boolean = false
)

@Dao
interface PendingNotificationDao {
    @Insert
    suspend fun insert(notification: PendingNotification): Long

    /**
     * Find an unconsumed notification matching this amount within a time window.
     * Amount must match to the paisa; the window absorbs the normal few-second
     * drift between a payment app's notification and the bank's SMS.
     */
    @Query("""
        SELECT * FROM pending_notifications
        WHERE consumed = 0
          AND amount = :amount
          AND timestamp BETWEEN :windowStart AND :windowEnd
        ORDER BY ABS(timestamp - :targetTime) ASC
        LIMIT 1
    """)
    suspend fun findMatch(amount: Double, windowStart: Long, windowEnd: Long, targetTime: Long): PendingNotification?

    @Query("UPDATE pending_notifications SET consumed = 1 WHERE id = :id")
    suspend fun markConsumed(id: Long)

    /** Housekeeping: unmatched notifications older than the cutoff are never going to match. */
    @Query("DELETE FROM pending_notifications WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM pending_notifications WHERE consumed = 0")
    suspend fun unconsumedCount(): Int
}
