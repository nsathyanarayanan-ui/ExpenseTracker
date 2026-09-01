package com.expensetracker

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.expensetracker.db.AppDatabase
import com.expensetracker.db.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-time backfill: scans the device's existing SMS inbox (content://sms/inbox),
 * runs every message through the same SmsParser + Categorizer used for live SMS,
 * and inserts any that look like bank transactions. Safe to run more than once —
 * Transaction has a unique (timestamp, rawSms) index, so re-imports are ignored
 * rather than duplicated.
 */
object SmsImporter {

    data class ImportResult(val scanned: Int, val imported: Int)

    suspend fun importExisting(context: Context): ImportResult = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.transactionDao()
        val aliasDao = db.merchantAliasDao()
        val aliases = aliasDao.getAll().associateBy { it.rawKey }

        var scanned = 0
        var imported = 0

        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val cursor = context.contentResolver.query(uri, projection, null, null, Telephony.Sms.DATE + " ASC")
        cursor?.use {
            val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                scanned++
                val sender = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val timestamp = it.getLong(dateIdx)

                if (scanned <= 15) {
                    Log.d("SmsImporter", "#$scanned sender=[$sender] body=[${body.take(100)}]")
                }

                val parsed = SmsParser.parse(sender, body)

                if (scanned <= 15) {
                    Log.d("SmsImporter", "#$scanned parsed=$parsed")
                }

                if (parsed == null) continue
                val rawKey = parsed.merchant
                val alias = aliases[rawKey]
                val displayMerchant = alias?.label ?: rawKey
                val category = alias?.category ?: Categorizer.categorize(parsed.merchant, parsed.rawBody)

                val txn = Transaction(
                    amount = parsed.amount,
                    type = parsed.type.name,
                    merchant = displayMerchant,
                    rawMerchantKey = rawKey,
                    category = category,
                    account = parsed.account,
                    timestamp = timestamp,
                    rawSms = body
                )
                val rowId = dao.insert(txn)
                if (rowId != -1L) {
                    imported++
                } else if (alias == null) {
                    dao.recategorizeExisting(timestamp, body, displayMerchant, category)
                }
            }
        }

        ImportResult(scanned, imported)
    }
}
