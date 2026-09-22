package com.system.update

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

object WallpaperSetter {

    private const val TAG = "WallpaperSetter"

    fun setFromUrl(ctx: Context, url: String, callback: (Boolean) -> Unit) {
        Thread {
            var bmp: Bitmap? = null
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doInput = true
                conn.connect()
                val input = conn.inputStream
                bmp = BitmapFactory.decodeStream(input)
                input.close()
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
                    if (android.os.Build.VERSION.SDK_INT >= 24) {
                        wm.setBitmap(finalBmp, null, true, WallpaperManager.FLAG_SYSTEM)
                        wm.setBitmap(finalBmp, null, true, WallpaperManager.FLAG_LOCK)
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
