# Expense Tracker (Android, Kotlin)

Reads incoming bank/UPI SMS on-device, parses debit/credit transactions,
categorizes them, stores everything locally in Room, and shows:

- Monthly spend vs. income
- Category pie chart + top-merchant bar chart
- "Spending flags" (high discretionary share, frequent small-ticket merchants)
- Exact-rupee savings opportunities
- A 0–100 Financial Health Score
- Per-category budget limits with push notifications when you cross 80% / 100%

All processing is on-device. No SMS content leaves the phone — there is no
backend/server in this project.

## How to build

1. Install **Android Studio** (Hedgehog or newer).
2. `File > Open` → select this `ExpenseTracker` folder.
3. Let Gradle sync (Android Studio will offer to create the Gradle wrapper —
   accept it, or run `gradle wrapper` once if you have Gradle installed locally).
4. Connect a device or start an emulator running **Android 8.0 (API 26) or higher**.
5. Run the `app` configuration.
6. On first launch, grant the **SMS** and **Notifications** permissions when prompted.
7. Open the (to-be-added) Settings screen from the menu to set a monthly ₹ limit
   per category — you'll get a notification when you cross 80% and again at 100%.

## Key files

| File | Purpose |
|---|---|
| `SmsReceiver.kt` | Listens for `SMS_RECEIVED`, hands text to the parser |
| `SmsParser.kt` | Regex-based extraction of amount/type/merchant from bank SMS |
| `Categorizer.kt` | Keyword-based category assignment (same logic as the earlier Excel/PDF report) |
| `InsightsEngine.kt` | Unnecessary-spend flags, savings opportunities, health score |
| `db/` | Room entities, DAO, database |
| `MainActivity.kt` | Dashboard UI: charts, flags, savings list |
| `SettingsActivity.kt` | Per-category monthly budget input |
| `notification/BudgetCheckWorker.kt` | Runs after each debit SMS, fires alerts if over budget |

## Extending SMS coverage

`SmsParser.BANK_SENDER_IDS` and the regex patterns cover common formats
(SBI, HDFC, ICICI, Axis, Standard Chartered, Kotak, generic UPI). If your
bank's SMS isn't detected, add its sender ID to that list and/or add a
new regex pattern — test against a real sample SMS string first.

## Known limitations / next steps

- Health-score "month-to-month consistency" component uses a placeholder
  until 3+ months of on-device history accumulate — wire it to real
  per-month stdev once `Transaction` history is populated.
- No cloud backup/sync — data lives only in the app's local Room database
  (uninstalling the app deletes it, matching the "on-device only" privacy goal).
- Reading *historical* SMS (beyond newly received ones) would need an
  additional one-time `ContentResolver` query against `content://sms/inbox`
  with the `READ_SMS` permission — not included by default to keep the
  first run privacy-conservative; easy to add if you want backfill.
