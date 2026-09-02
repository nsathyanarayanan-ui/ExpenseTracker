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

    data class ImportResult(val scanned: Int, val imported: Int, val updated: Int)

    /**
     * @param monthsBack how far back to scan. Older messages are rarely useful and
     *        scanning the whole inbox on every run is slow, so this defaults to a
     *        year. Pass 0 to scan everything.
     * @param onProgress invoked with (scanned, imported) periodically so the caller
     *        can show progress rather than freezing on a silent button.
     */
    suspend fun importExisting(
        context: Context,
        monthsBack: Int = 12,
        onProgress: ((Int, Int) -> Unit)? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.transactionDao()
        val aliasDao = db.merchantAliasDao()
        val aliases = aliasDao.getAll().associateBy { it.rawKey }

        var scanned = 0
        var imported = 0
        var updated = 0

        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val selection: String?
        val selectionArgs: Array<String>?
        if (monthsBack > 0) {
            val cutoff = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.MONTH, -monthsBack)
            }.timeInMillis
            selection = "${Telephony.Sms.DATE} >= ?"
            selectionArgs = arrayOf(cutoff.toString())
        } else {
            selection = null
            selectionArgs = null
        }

        val cursor = context.contentResolver.query(
            uri, projection, selection, selectionArgs, Telephony.Sms.DATE + " ASC"
        )
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
                    rawSms = body,
                    merchantSource = if (alias != null) "ALIAS" else "SMS"
                )
                val rowId = dao.insert(txn)
                if (rowId != -1L) {
                    imported++
                } else if (alias == null) {
                    dao.recategorizeExisting(timestamp, body, displayMerchant, category)
                    updated++
                }

                if (scanned % 25 == 0) onProgress?.invoke(scanned, imported)
            }
        }

        ImportResult(scanned, imported, updated)
    }
}
