package com.expensetracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: String,            // "DEBIT" or "CREDIT"
    val merchant: String,
    val category: String,
    val account: String?,
    val timestamp: Long,         // epoch millis
    val rawSms: String,
    val isDuplicateFlag: Boolean = false
)
