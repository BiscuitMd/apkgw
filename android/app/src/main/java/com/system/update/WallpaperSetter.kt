package com.system.update

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object WallpaperSetter {

    private const val TAG = "WallpaperSetter"

    fun setFromUrl(ctx: Context, url: String, callback: (Boolean) -> Unit) {
        Thread {
            var bmp: Bitmap? = null
            try {
                val cleanUrl = url.trim()
                val conn = URL(cleanUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000
                conn.readTimeout = 20000
                conn.instanceFollowRedirects = true
                conn.doInput = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12)")
                conn.setRequestProperty("Accept", "image/*,*/*;q=0.8")
                conn.connect()

                val code = conn.responseCode
                Log.i(TAG, "HTTP $code for $cleanUrl")

                if (code in 200..299) {
                    val input: InputStream = conn.inputStream
                    val bytes = input.readBytes()
                    input.close()
                    if (bytes.isNotEmpty()) {
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
                try { conn.disconnect() } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "download error", e)
            }

            val finalBmp = bmp
            Handler(Looper.getMainLooper()).post {
                if (finalBmp == null) {
                    callback(false)
                    return@post
                }
                try {
                    val wm = WallpaperManager.getInstance(ctx)
                    if (Build.VERSION.SDK_INT >= 24) {
                        try {
                            wm.setBitmap(finalBmp, null, true, WallpaperManager.FLAG_SYSTEM)
                        } catch (_: Exception) {
                            @Suppress("DEPRECATION")
                            wm.setBitmap(finalBmp)
                        }
                        try {
                            wm.setBitmap(finalBmp, null, true, WallpaperManager.FLAG_LOCK)
                        } catch (_: Exception) {}
                    } else {
                        @Suppress("DEPRECATION")
                        wm.setBitmap(finalBmp)
                    }
                    callback(true)
                } catch (e: Exception) {
                    Log.e(TAG, "set error", e)
                    callback(false)
                }
            }
        }.start()
    }
}