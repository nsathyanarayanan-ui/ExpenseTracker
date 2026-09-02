package com.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.expensetracker.db.AppDatabase
import com.expensetracker.db.Transaction
import com.expensetracker.notification.BudgetCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // A BroadcastReceiver's process can be killed as soon as onReceive returns.
        // Database writes happen on a background coroutine, so without goAsync() the
        // system may terminate us mid-write and silently lose the transaction.
        // pendingResult.finish() tells Android we're done and it's safe to stop.
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)

                for (sms in messages) {
                    val sender = sms.originatingAddress ?: continue
                    val body = sms.messageBody ?: continue
                    val timestamp = sms.timestampMillis

                    val parsed = SmsParser.parse(sender, body) ?: continue
                    val rawKey = parsed.merchant
                    val category = Categorizer.categorize(parsed.merchant, parsed.rawBody)

                    val alias = db.merchantAliasDao().getForKey(rawKey)
                    val displayMerchant = alias?.label ?: rawKey
                    val finalCategory = alias?.category ?: category

                    val txn = Transaction(
                        amount = parsed.amount,
                        type = parsed.type.name,
                        merchant = displayMerchant,
                        rawMerchantKey = rawKey,
                        category = finalCategory,
                        account = parsed.account,
                        timestamp = timestamp,
                        rawSms = body,
                        // A manual alias is the user's own decision — mark it so the
                        // notification matcher won't later overwrite it.
                        merchantSource = if (alias != null) "ALIAS" else "SMS"
                    )
                    val rowId = db.transactionDao().insert(txn)

                    // If a UPI app notification for this same payment already arrived,
                    // use its merchant name — that's the piece the bank SMS lacks.
                    var effectiveCategory = finalCategory
                    if (rowId != -1L && alias == null) {
                        val enrichedMerchant = NotificationMatcher.onTransactionStored(
                            context = context,
                            transactionId = rowId,
                            amount = parsed.amount,
                            timestamp = timestamp,
                            rawSms = body
                        )
                        if (enrichedMerchant != null) {
                            effectiveCategory = Categorizer.categorize(enrichedMerchant, body)
                        }
                    }

                    if (txn.type == "DEBIT" && rowId != -1L) {
                        // Keyed by category with KEEP: several SMS arriving together used to
                        // queue a separate worker each, spamming duplicate budget alerts.
                        val work = OneTimeWorkRequestBuilder<BudgetCheckWorker>()
                            .setInputData(workDataOf("category" to effectiveCategory))
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            "budget_check_$effectiveCategory",
                            ExistingWorkPolicy.KEEP,
                            work
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Failed to process incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
