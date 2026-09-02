package com.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.expensetracker.db.AppDatabase
import com.expensetracker.db.Transaction
import com.expensetracker.notification.BudgetCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val sender = sms.originatingAddress ?: continue
            val body = sms.messageBody ?: continue
            val timestamp = sms.timestampMillis

            val parsed = SmsParser.parse(sender, body) ?: continue
            val rawKey = parsed.merchant
            val category = Categorizer.categorize(parsed.merchant, parsed.rawBody)

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
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

                // After storing, kick off a budget check for this category
                if (txn.type == "DEBIT") {
                    val work = OneTimeWorkRequestBuilder<BudgetCheckWorker>()
                        .setInputData(workDataOf("category" to effectiveCategory))
                        .build()
                    WorkManager.getInstance(context).enqueue(work)
                }
            }
        }
    }
}
