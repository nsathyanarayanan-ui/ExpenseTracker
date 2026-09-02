package com.expensetracker

import java.util.regex.Pattern

data class ParsedNotification(
    val amount: Double,
    val merchant: String,
    val timestamp: Long
)

/**
 * Extracts merchant + amount from UPI payment app notifications.
 *
 * These notifications are the one place the merchant name reliably appears for UPI
 * payments — bank SMS for the same transaction often contains only a destination
 * account number. Matching the two together is what lets the app show "Swiggy"
 * instead of "Account XX2063".
 */
object NotificationParser {

    val UPI_APP_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user",  // Google Pay India
        "com.phonepe.app",                          // PhonePe
        "net.one97.paytm",                          // Paytm
        "in.org.npci.upiapp",                       // BHIM
        "com.amazon.mShop.android.shopping",        // Amazon Pay
        "com.mobikwik_new",                         // MobiKwik
        "com.freecharge.android",                   // Freecharge
        "in.amazon.mShop.android.shopping"
    )

    private val AMOUNT_PATTERN = Pattern.compile(
        "(?:₹|Rs\\.?|INR)\\s?([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE
    )

    // Notification text varies by app. Common shapes:
    //   "You paid ₹420 to Swiggy"
    //   "₹420 paid to Swiggy"
    //   "Paid ₹420 to Swiggy successfully"
    //   "Money sent to Swiggy"
    //   "You've sent ₹420 to Ramesh Kumar"
    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("(?:paid|sent|transferred)\\s+(?:₹|Rs\\.?|INR)?\\s?[0-9,.]*\\s*to\\s+([^.,\\n!]{2,40})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("to\\s+([A-Za-z0-9 &._'@\\-]{2,40})\\s+(?:for|via|using|successful)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^([A-Za-z0-9 &._'@\\-]{2,40})\\s+(?:received|got)", Pattern.CASE_INSENSITIVE)
    )

    // Notifications that look transactional but aren't a completed payment
    private val IGNORE_MARKERS = listOf(
        "request", "reminder", "offer", "cashback offer", "scratch card",
        "failed", "declined", "pending", "will be", "expire"
    )

    fun parse(packageName: String, title: String?, text: String?, timestamp: Long): ParsedNotification? {
        if (packageName !in UPI_APP_PACKAGES) return null

        val combined = listOfNotNull(title, text).joinToString(" ").trim()
        if (combined.isBlank()) return null

        val lower = combined.lowercase()
        if (IGNORE_MARKERS.any { lower.contains(it) }) return null

        val amountMatcher = AMOUNT_PATTERN.matcher(combined)
        if (!amountMatcher.find()) return null
        val amount = amountMatcher.group(1)!!.replace(",", "").toDoubleOrNull() ?: return null

        var merchant: String? = null
        for (p in MERCHANT_PATTERNS) {
            val m = p.matcher(combined)
            if (m.find()) {
                merchant = m.group(1)?.trim()?.trimEnd('.', ',', '!')
                if (!merchant.isNullOrBlank()) break
            }
        }

        if (merchant.isNullOrBlank()) return null
        if (merchant.length < 2) return null

        return ParsedNotification(amount, merchant, timestamp)
    }
}
