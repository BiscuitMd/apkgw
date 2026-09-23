package com.system.update

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class PrankLCDService : Service() {

    companion object {
        const val NOTIF_ID = 400
        @Volatile var isRunning: Boolean = false
    }

    private lateinit var wm: WindowManager
    private var overlay: View? = null
    private var handler: Handler? = null
    private var offset = 0f

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        handler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stop = intent?.getBooleanExtra("stop", false) ?: false
        if (stop) {
            stopLCD()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        isRunning = true
        showOverlay()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun showOverlay() {
        try {
            removeOverlay()

            val view = object : View(this) {
                private val paint = Paint().apply {
                    strokeWidth = 4f
                    style = Paint.Style.STROKE
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    canvas.drawColor(Color.parseColor("#CC000000"))

                    // Green lines bergerak
                    val lineSpacing = 60f
                    val h = height.toFloat()
                    var y = offset
                    var i = 0
                    while (y < h) {
                        val alpha = (128 + 100 * kotlin.math.sin((y / h * 6.28f) + offset / 100)).toInt().coerceIn(0, 255)
                        paint.color = Color.argb(
                            alpha,
                            0,
                            255,
                            (128 + 100 * kotlin.math.cos(y / 40f + offset / 50)).toInt().coerceIn(0, 255)
                        )
                        canvas.drawLine(0f, y, width.toFloat(), y, paint)
                        y += lineSpacing
                        i++
                    }

                    // Noise text
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(80, 0, 255, 0)
                    paint.textSize = 30f
                    canvas.drawText("SYSTEM FAILURE", width / 4f, height / 2f, paint)
                    canvas.drawText("LCD ERROR 0x00${(offset.toInt() % 100)}", width / 4f, height / 2f + 40f, paint)
                }
            }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            wm.addView(view, params)
            overlay = view

            // Animate offset
            handler?.post(object : Runnable {
                override fun run() {
                    if (!isRunning) return
                    offset += 3f
                    view.invalidate()
                    handler?.postDelayed(this, 30)
                }
            })
        } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        try { overlay?.let { wm.removeView(it) } } catch (_: Exception) {}
        overlay = null
    }

    private fun stopLCD() {
        isRunning = false
        handler?.removeCallbacksAndMessages(null)
        removeOverlay()
    }

    override fun onDestroy() {
        stopLCD()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}