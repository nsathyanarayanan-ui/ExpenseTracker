package com.expensetracker.db

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "budgets")
data class Budget(
    @androidx.room.PrimaryKey val category: String,
    val monthlyLimit: Double
)

@androidx.room.Dao
interface BudgetDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun setBudget(budget: Budget)

    @androidx.room.Query("SELECT * FROM budgets")
    suspend fun getAll(): List<Budget>

    @androidx.room.Query("SELECT * FROM budgets WHERE category = :category LIMIT 1")
    suspend fun getForCategory(category: String): Budget?
}

@Database(entities = [Transaction::class, Budget::class, MerchantAlias::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun merchantAliasDao(): MerchantAliasDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
