package com.expensetracker

object Categorizer {

    // category -> keyword list (merchant name matched, case-insensitive)
    private val RULES: LinkedHashMap<String, List<String>> = linkedMapOf(
        "Food Delivery" to listOf("swiggy", "zomato", "bundl technologies"),
        "Dining Out & Snacks" to listOf(
            "restaurant", "cafe", "hotel", "bhavan", "sweets", "bakery", "food", "biryani",
            "dine", "eatery", "snacks", "juice", "coffee"
        ),
        "Groceries" to listOf(
            "bigbasket", "grocery", "supermarket", "mart", "provisions", "fruits", "vegetable",
            "dmart", "reliance fresh", "more supermarket"
        ),
        "Healthcare" to listOf(
            "pharmacy", "medical", "hospital", "clinic", "diagnostic", "apollo", "medplus",
            "healthcare", "insurance"
        ),
        "Utilities & Bills" to listOf(
            "broadband", "airtel", "jio", "vi ", "vodafone", "electricity", "power", "gas",
            "water board", "tneb", "bses", "corporation", "dth", "tata play", "mygate"
        ),
        "Fuel & Transport" to listOf(
            "petrol", "fuel", "hpcl", "bpcl", "iocl", "indian oil", "fastag", "ola", "uber",
            "rapido", "metro", "railways", "irctc"
        ),
        "Education" to listOf("school", "college", "tuition", "udemy", "coursera", "byju", "fees", "academy"),
        "Entertainment" to listOf(
            "bookmyshow", "pvr", "inox", "cinepolis", "netflix", "hotstar", "prime video",
            "spotify", "amusement", "timezone", "play zone"
        ),
        "Shopping" to listOf(
            "myntra", "amazon", "flipkart", "zudio", "trends", "decathlon", "reliance trends",
            "shoe", "footwear", "clothing", "fashion", "mall"
        ),
        "Subscriptions" to listOf("google play", "apple.com/bill", "subscription", "playstore"),
        "Travel" to listOf("irctc", "makemytrip", "goibibo", "ixigo", "airlines", "indigo", "yatra"),
    )

    fun categorize(merchant: String): String {
        val m = merchant.lowercase()

        // Transfer-only SMS with no merchant name — just "Account XX1234"
        if (m.startsWith("account xx")) return "Bank Transfer"

        for ((category, keywords) in RULES) {
            if (keywords.any { m.contains(it) }) return category
        }
        // Heuristic: names without company suffixes look like personal transfers
        val companyMarkers = listOf(
            "private limited", "pvt ltd", "ltd", "llp", "enterprises", "stores", "services",
            "technologies", "solutions", "corporation", "company"
        )
        return if (companyMarkers.none { m.contains(it) } && m.split(" ").size <= 4) {
            "Personal Transfers"
        } else {
            "Other / Miscellaneous"
        }
    }

    /** Categories treated as discretionary/"unnecessary-if-overdone" for the insights screen. */
    val DISCRETIONARY_CATEGORIES = setOf(
        "Food Delivery", "Dining Out & Snacks", "Entertainment", "Shopping", "Subscriptions"
    )
}
