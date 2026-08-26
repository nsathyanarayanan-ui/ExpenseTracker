package com.expensetracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expensetracker.MainActivity
import com.expensetracker.R
import com.expensetracker.db.AppDatabase
import java.util.Calendar

class BudgetCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val category = inputData.getString("category") ?: return Result.success()
        val db = AppDatabase.getInstance(applicationContext)

        val budget = db.budgetDao().getForCategory(category) ?: return Result.success()

        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val monthStart = cal.timeInMillis
        val monthEnd = System.currentTimeMillis()

        val spent = db.transactionDao().categoryTotal(category, monthStart, monthEnd) ?: 0.0

        if (spent > budget.monthlyLimit) {
            val overBy = spent - budget.monthlyLimit
            NotificationHelper.showBudgetAlert(
                applicationContext,
                category,
                spent,
                budget.monthlyLimit,
                overBy
            )
        } else if (spent > budget.monthlyLimit * 0.8) {
            NotificationHelper.showApproachingAlert(applicationContext, category, spent, budget.monthlyLimit)
        }

        return Result.success()
    }
}

object NotificationHelper {
    private const val CHANNEL_ID = "budget_alerts"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Budget Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts when a spending category exceeds its budget" }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    fun showBudgetAlert(context: Context, category: String, spent: Double, limit: Double, overBy: Double) {
        ensureChannel(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Budget exceeded: $category")
            .setContentText("Spent ₹%.0f of ₹%.0f limit (₹%.0f over)".format(spent, limit, overBy))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(category.hashCode(), notif)
    }

    fun showApproachingAlert(context: Context, category: String, spent: Double, limit: Double) {
        ensureChannel(context)
        val pct = (spent / limit * 100).toInt()
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$category budget at $pct%")
            .setContentText("Spent ₹%.0f of ₹%.0f this month".format(spent, limit))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(category.hashCode() + 1, notif)
    }
}
