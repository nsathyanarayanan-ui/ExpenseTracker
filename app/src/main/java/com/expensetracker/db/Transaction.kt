package com.expensetracker.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["timestamp", "rawSms"], unique = true)]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: String,            // "DEBIT" or "CREDIT"
    val merchant: String,        // display name — may be overridden by an alias label or a notification match
    val rawMerchantKey: String,  // original identifier (e.g. "Account XX2063") — stable, used for alias lookup
    val category: String,
    val account: String?,
    val timestamp: Long,         // epoch millis
    val rawSms: String,
    val isDuplicateFlag: Boolean = false,
    // "SMS" = merchant came from the bank message itself (or is an unresolved account number)
    // "NOTIFICATION" = enriched from a UPI app notification
    // "ALIAS" = manually labeled by the user; never overwritten automatically
    val merchantSource: String = "SMS"
)
