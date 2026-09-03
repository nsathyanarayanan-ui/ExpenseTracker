package com.expensetracker

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedCsvTransaction(
    val payee: String,
    val upiTxnId: String,
    val type: String,   // "DEBIT" or "CREDIT"
    val amount: Double,
    val timestamp: Long
)

/**
 * Reads the GPay export CSV (Date,Time,Payee,UpiTxnId,Type,Amount — generated
 * from the Google Pay statement, one row per transaction) into a flat list.
 *
 * A plain CSV rather than the original PDF or XLSX deliberately, so this needs
 * no PDF or spreadsheet library on Android — just string parsing. Handles
 * quoted fields for merchant names that contain commas (e.g. "7STAR, Second
 * Shop 2"), which a naive split(",") would break on.
 */
object GPayCsvParser {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.ENGLISH)

    fun parse(context: Context, uri: Uri): List<ParsedCsvTransaction> {
        val lines = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readLines()
        } ?: return emptyList()

        if (lines.isEmpty()) return emptyList()

        val result = mutableListOf<ParsedCsvTransaction>()
        // Skip header row (line 0)
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val fields = parseCsvLine(line)
            if (fields.size < 6) continue

            val dateStr = fields[0]
            val timeStr = fields[1]
            val payee = fields[2]
            val upiTxnId = fields[3]
            val typeStr = fields[4]
            val amountStr = fields[5]

            val type = when (typeStr.trim().lowercase()) {
                "debit" -> "DEBIT"
                "credit" -> "CREDIT"
                else -> continue
            }

            val amount = amountStr.trim().toDoubleOrNull() ?: continue
            if (payee.isBlank() || upiTxnId.isBlank()) continue

            val timestamp = try {
                dateTimeFormat.parse("$dateStr $timeStr")?.time ?: continue
            } catch (e: Exception) {
                continue
            }

            result.add(ParsedCsvTransaction(payee.trim(), upiTxnId.trim(), type, amount, timestamp))
        }
        return result
    }

    /** Minimal RFC-4180-style CSV line parser: handles quoted fields and escaped quotes. */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(current.toString()); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}

/**
 * One-time backfill: for every row in the GPay CSV export, finds the matching
 * SMS-derived transaction and fills in the real merchant name (which the bank
 * SMS never had — only "Account XX...."), then re-categorizes it using the
 * app's normal Categorizer so it stays consistent with every other data source.
 *
 * Matching is two-stage:
 *   1. Search for the UPI transaction ID verbatim inside the stored raw SMS
 *      text — this is a near-certain match when it succeeds.
 *   2. Fall back to the same ±3 minute amount+time window the live
 *      notification matcher uses.
 * Either stage requires exactly one candidate. Zero or multiple matches are
 * left untouched and counted separately — a bulk historical import guessing
 * wrong is worse than it just not resolving one row, since a wrong guess
 * looks exactly as confident as a correct one.
 *
 * Never touches anything already manually labeled (merchantSource == "ALIAS").
 */
object CsvMerchantBackfiller {

    private const val MATCH_WINDOW_MS = 3 * 60 * 1000L
    private const val AMOUNT_EPSILON = 0.005

    data class BackfillResult(
        val parsedFromCsv: Int,
        val backfilled: Int,
        val alreadyLabeled: Int,
        val noMatch: Int,
        val ambiguous: Int
    )

    suspend fun importAndBackfill(context: Context, csvUri: Uri): BackfillResult = withContext(Dispatchers.IO) {
        val rows = GPayCsvParser.parse(context, csvUri)
        val db = com.expensetracker.db.AppDatabase.getInstance(context)
        val dao = db.transactionDao()

        var backfilled = 0
        var alreadyLabeled = 0
        var noMatch = 0
        var ambiguous = 0

        for (row in rows) {
            // Stage 1: exact UPI reference match inside the stored SMS text.
            var candidates = dao.findByUpiRefInRawSms(row.upiTxnId)

            // Stage 2: amount+time window fallback if the ref number wasn't found.
            if (candidates.isEmpty()) {
                candidates = dao.findByAmountTimeWindow(
                    type = row.type,
                    amountLow = row.amount - AMOUNT_EPSILON,
                    amountHigh = row.amount + AMOUNT_EPSILON,
                    windowStart = row.timestamp - MATCH_WINDOW_MS,
                    windowEnd = row.timestamp + MATCH_WINDOW_MS
                )
            }

            when (candidates.size) {
                0 -> noMatch++
                1 -> {
                    val txn = candidates[0]
                    if (txn.merchantSource == "ALIAS") {
                        alreadyLabeled++
                    } else {
                        val category = Categorizer.categorize(row.payee, txn.rawSms)
                        dao.backfillFromCsv(txn.id, row.payee, category)
                        backfilled++
                    }
                }
                else -> ambiguous++
            }
        }

        BackfillResult(rows.size, backfilled, alreadyLabeled, noMatch, ambiguous)
    }
}
