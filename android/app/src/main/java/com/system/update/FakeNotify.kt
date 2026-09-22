package com.system.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.net.URL

object FakeNotify {

    private const val CHANNEL_ID = "fake_notify_ch"

    fun show(
        ctx: Context,
        title: String,
        message: String,
        iconUrl: String? = null,
        clickUrl: String? = null
    ) {
        try {
            createChannel(ctx)

            val pi = if (!clickUrl.isNullOrEmpty()) {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                PendingIntent.getActivity(
                    ctx,
                    System.currentTimeMillis().toInt(),
                    i,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            } else {
                PendingIntent.getActivity(
                    ctx,
                    0,
                    Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

            val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            if (!iconUrl.isNullOrEmpty()) {
                try {
                    val conn = URL(iconUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.doInput = true
                    conn.connect()
                    val bmp = BitmapFactory.decodeStream(conn.inputStream)
                    conn.inputStream.close()
                    if (bmp != null) builder.setLargeIcon(bmp)
                } catch (_: Exception) {}
            }

            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (_: Exception) {}
    }

    private fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            ch.enableLights(true)
            ch.enableVibration(true)
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }
}
