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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        private const val PREFS = "exoid_app_lock"
        var instance: AppLockAccessibilityService? = null

        fun setLockedApp(ctx: Context, pkg: String, pin: String) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString(pkg, pin).apply()
        }

        fun unlockApp(ctx: Context, pkg: String) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().remove(pkg).apply()
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val pin = prefs.getString(pkg, null)
        if (pin == null) {
            // kalau app di-unlock tapi masih pake overlay lama, remove
            if (currentPkg == pkg) hideOverlay()
            return
        }

        if (unlockedTemp.contains(pkg)) return
        if (currentPkg == pkg && currentOverlay != null) return

        showLockOverlay(pkg, pin)
    }

    override fun onInterrupt() {}

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
                setPadding(dp(24), dp(32), dp(24), dp(32))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 40f
                    setColor(Color.parseColor("#CC1A0000"))
                    setStroke(4, Color.parseColor("#00FF66"))
                }
            }

            val icon = TextView(this).apply {
                text = "\uD83D\uDD12"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 60f)
                gravity = Gravity.CENTER
            }

            val title = TextView(this).apply {
                text = "APP TERKUNCI"
                setTextColor(Color.parseColor("#00FF66"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                letterSpacing = 0.2f
                setPadding(0, dp(12), 0, dp(4))
            }

            val sub = TextView(this).apply {
                text = "App ini dikunci oleh EXOID ENGINE"
                setTextColor(Color.parseColor("#9A9A9A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(16))
            }

            val pkgName = TextView(this).apply {
                text = pkg
                setTextColor(Color.parseColor("#D4AF37"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(20))
            }

            val pinInput = EditText(this).apply {
                hint = "Masukkan PIN"
                setHintTextColor(Color.parseColor("#3A0000"))
                setTextColor(Color.parseColor("#00FF66"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                gravity = Gravity.CENTER
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 16f
                    setColor(Color.parseColor("#CC141414"))
                    setStroke(2, Color.parseColor("#00FF66"))
                }
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }

            val status = TextView(this).apply {
                text = ""
                setTextColor(Color.parseColor("#E60000"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(8))
            }

            val btn = Button(this).apply {
                text = "BUKA"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 20f
                    setColor(Color.parseColor("#B30000"))
                    setStroke(2, Color.parseColor("#00FF66"))
                }
            }

            btn.setOnClickListener {
                if (pinInput.text.toString().trim() == correctPin) {
                    unlockedTemp.add(pkg)
                    hideOverlay()
                    // Force kill app biar restart
                    try {
                        val intent = Intent(Intent.ACTION_MAIN)
                        intent.addCategory(Intent.CATEGORY_HOME)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    } catch (_: Exception) {}
                } else {
                    status.text = "PIN SALAH"
                }
            }

            card.addView(icon)
            card.addView(title)
            card.addView(sub)
            card.addView(pkgName)
            card.addView(pinInput)
            card.addView(btn)
            card.addView(status)

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
                WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )

            wm.addView(root, params)
            currentOverlay = root
        } catch (_: Exception) {}
    }

    private fun hideOverlay() {
        try { currentOverlay?.let { wm.removeView(it) } } catch (_: Exception) {}
        currentOverlay = null
        currentPkg = null
    }

    override fun onDestroy() {
        hideOverlay()
        instance = null
        super.onDestroy()
    }
}
