package com.expensetracker.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "merchant_aliases")
data class MerchantAlias(
    @PrimaryKey val rawKey: String,   // e.g. "Account XX2063" — the raw unlabeled identifier
    val label: String,                // e.g. "Swiggy" or "Mom"
    val category: String              // e.g. "Food Delivery" or "Personal Transfers"
)

@Dao
interface MerchantAliasDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: MerchantAlias)

    @Query("SELECT * FROM merchant_aliases")
    suspend fun getAll(): List<MerchantAlias>

    @Query("SELECT * FROM merchant_aliases WHERE rawKey = :rawKey LIMIT 1")
    suspend fun getForKey(rawKey: String): MerchantAlias?

    @Query("SELECT DISTINCT rawMerchantKey FROM transactions WHERE rawMerchantKey LIKE 'Account XX%'")
    suspend fun getUnlabeledAccountKeys(): List<String>
}
