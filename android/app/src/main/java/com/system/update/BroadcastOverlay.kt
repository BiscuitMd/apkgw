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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object BroadcastOverlay {

    private var currentView: View? = null
    private lateinit var wm: WindowManager

    private fun dp(ctx: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()

    fun show(ctx: Context, title: String, message: String, durationSec: Int = 5) {
        try {
            wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // Remove old overlay
            currentView?.let {
                try { wm.removeView(it) } catch (_: Exception) {}
            }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(ctx, 40)
            }

            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 40f
                    setColor(Color.parseColor("#F00F0F0F"))
                    setStroke(2, Color.parseColor("#D4AF37"))
                }
            }

            val topRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val icon = TextView(ctx).apply {
                text = "\uD83D\uDD14"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setPadding(0, 0, dp(ctx, 12), 0)
            }
            topRow.addView(icon)

            val appName = TextView(ctx).apply {
                text = "EXOID ENGINE"
                setTextColor(Color.parseColor("#D4AF37"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.2f
            }
            topRow.addView(appName)

            val timeTxt = TextView(ctx).apply {
                text = "  •  sekarang"
                setTextColor(Color.parseColor("#9A9A9A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            }
            topRow.addView(timeTxt)

            container.addView(topRow)

            val titleView = TextView(ctx).apply {
                text = title
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(ctx, 8), 0, 0)
            }
            container.addView(titleView)

            val bodyView = TextView(ctx).apply {
                text = message
                setTextColor(Color.parseColor("#E0E0E0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(ctx, 4), 0, 0)
                maxLines = 4
            }
            container.addView(bodyView)

            // Slide in animation
            container.translationY = -200f
            container.alpha = 0f

            wm.addView(container, params)
            currentView = container

            ObjectAnimator.ofFloat(container, "translationY", -200f, 0f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(container, "alpha", 0f, 1f).apply {
                duration = 400
                start()
            }

            // Slide out after duration
            Handler(Looper.getMainLooper()).postDelayed({
                ObjectAnimator.ofFloat(container, "translationY", 0f, -200f).apply {
                    duration = 400
                    start()
                }
                ObjectAnimator.ofFloat(container, "alpha", 1f, 0f).apply {
                    duration = 400
                    start()
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    try { wm.removeView(container) } catch (_: Exception) {}
                    if (currentView === container) currentView = null
                }, 450)
            }, durationSec * 1000L)

        } catch (_: Exception) {}
    }

    fun hide(ctx: Context) {
        try {
            currentView?.let { wm.removeView(it) }
            currentView = null
        } catch (_: Exception) {}
    }
}
