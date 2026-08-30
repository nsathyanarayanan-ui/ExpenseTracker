package com.expensetracker

import java.util.Locale
import java.util.regex.Pattern

/**
 * Parses raw bank/UPI SMS text into a structured transaction.
 * Tuned for common Indian bank SMS formats (SBI, HDFC, ICICI, Axis, Standard
 * Chartered, Kotak, generic UPI apps). Extend BANK_SENDER_IDS and the regex
 * list as needed for banks not covered.
 */
data class ParsedSms(
    val amount: Double,
    val type: TxnType,          // DEBIT or CREDIT
    val merchant: String,
    val account: String?,       // last 4 digits if present
    val rawBody: String
)

enum class TxnType { DEBIT, CREDIT }

object SmsParser {

    // Sender IDs banks typically use (varies by carrier/registration, extend as needed)
    val BANK_SENDER_IDS = listOf(
        "SBIINB", "SBIUPI", "HDFCBK", "ICICIB", "AXISBK", "SCBANK", "KOTAKB",
        "PAYTM", "GPAY", "UPI", "IDFCFB", "PNBSMS", "CANBNK", "BOIIND"
    )

    // Amount pattern: Rs.1,234.56 / INR 1234 / Rs 500
    private val AMOUNT_PATTERN = Pattern.compile(
        "(?:Rs\\.?|INR)\\s?([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE
    )

    private val DEBIT_KEYWORDS = listOf(
        "debited", "spent", "paid", "withdrawn", "purchase of", "sent"
    )
    private val CREDIT_KEYWORDS = listOf(
        "credited", "received", "deposited", "refund"
    )

    // Merchant / payee extraction patterns, tried in order
    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("(?:at|to)\\s+([A-Za-z0-9 &._'\\-]{2,40})(?:\\s+on|\\s+dt|\\.|,|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:UPI[/-].*?[/-])([A-Za-z0-9 &._'\\-]{2,40})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("info[:\\-]\\s*([A-Za-z0-9 &._'\\-]{2,40})", Pattern.CASE_INSENSITIVE)
    )

    // Fallback for transfer-only SMS with no merchant name — just a destination/source account.
    // Handles both "credited to a/c XX2063" and "debited from a/c no. XXXXXXX8769" wordings.
    private val COUNTERPARTY_ACCOUNT_PATTERN = Pattern.compile(
        "(?:credited to|debited from) a/c(?:\\s*no\\.?)?\\s*X*(\\d{3,6})", Pattern.CASE_INSENSITIVE
    )

    private val ACCOUNT_PATTERN = Pattern.compile(
        "(?:a/c|acct|account)\\D{0,5}(?:no\\.?)?\\D{0,3}(?:x{2,}|\\*{2,})?(\\d{4})",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Returns null if the message does not look like a bank transaction SMS.
     */
    fun parse(sender: String, body: String): ParsedSms? {
        val looksLikeBank = BANK_SENDER_IDS.any { sender.uppercase(Locale.ROOT).contains(it) } ||
                AMOUNT_PATTERN.matcher(body).find() &&
                (DEBIT_KEYWORDS.any { body.contains(it, ignoreCase = true) } ||
                        CREDIT_KEYWORDS.any { body.contains(it, ignoreCase = true) })

        if (!looksLikeBank) return null

        val amountMatcher = AMOUNT_PATTERN.matcher(body)
        if (!amountMatcher.find()) return null
        val amount = amountMatcher.group(1)!!.replace(",", "").toDoubleOrNull() ?: return null

        // Determine DEBIT vs CREDIT by whichever keyword appears earliest in the message —
        // bank SMS always states what happened to *your* account first, then the
        // counterparty's account second (e.g. "...is credited for Rs.X... and debited
        // from a/c Y" — that "debited" refers to the OTHER party, not you).
        val debitIdx = DEBIT_KEYWORDS
            .mapNotNull { kw -> body.indexOf(kw, ignoreCase = true).takeIf { it >= 0 } }
            .minOrNull() ?: Int.MAX_VALUE
        val creditIdx = CREDIT_KEYWORDS
            .mapNotNull { kw -> body.indexOf(kw, ignoreCase = true).takeIf { it >= 0 } }
            .minOrNull() ?: Int.MAX_VALUE

        val type = when {
            debitIdx == Int.MAX_VALUE && creditIdx == Int.MAX_VALUE -> return null
            debitIdx <= creditIdx -> TxnType.DEBIT
            else -> TxnType.CREDIT
        }

        // Reject OTP / promotional messages explicitly
        if (body.contains("OTP", ignoreCase = true) || body.contains("one time password", ignoreCase = true)) {
            return null
        }

        var merchant = "Unknown"
        for (p in MERCHANT_PATTERNS) {
            val m = p.matcher(body)
            if (m.find()) {
                merchant = m.group(1)!!.trim()
                break
            }
        }
        if (merchant == "Unknown") {
            val am = COUNTERPARTY_ACCOUNT_PATTERN.matcher(body)
            if (am.find()) {
                merchant = "Account XX${am.group(1)}"
            }
        }

        var account: String? = null
        val accMatcher = ACCOUNT_PATTERN.matcher(body)
        if (accMatcher.find()) account = accMatcher.group(1)

        return ParsedSms(amount, type, merchant, account, body)
    }
}
