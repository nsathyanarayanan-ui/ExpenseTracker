package com.expensetracker

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens for notifications from UPI payment apps to capture merchant names.
 *
 * Only notifications from known payment app packages are inspected, and only
 * the amount and merchant name are extracted — nothing is stored from any other
 * app, and nothing leaves the device.
 */
class UpiNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (packageName !in NotificationParser.UPI_APP_PACKAGES) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()

        val parsed = NotificationParser.parse(packageName, title, text, sbn.postTime) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            NotificationMatcher.onNotification(applicationContext, parsed)
        }
    }
}
