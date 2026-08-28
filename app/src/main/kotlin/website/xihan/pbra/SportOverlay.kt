package website.xihan.pbra

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * In-activity overlay for Xiaomi Fitness sporting screens.
 * This is injected into the Activity DecorView, so it does NOT need SYSTEM_ALERT_WINDOW.
 */
object SportOverlay {
    private val handler = Handler(Looper.getMainLooper())
    private val bubbles = WeakHashMap<Activity, TextView>()

    fun isSportingActivity(activity: Activity): Boolean = isSportingActivityName(activity.javaClass.name)

    fun isSportingActivityName(name: String): Boolean =
        name.contains(".sport.view.sporting.") ||
            name.contains(".sport_eco.view.sporting.") ||
            name.endsWith(".sport.view.lockscreen.view.SportLockScreenActivity") ||
            name.endsWith(".sport_eco.view.lockscreen.view.SportLockScreenActivity")

    fun sync(activity: Activity) {
        if (!isSportingActivity(activity)) return
        if (Settings.sportOverlayEnabled) attach(activity) else detach(activity)
    }

    fun attach(activity: Activity) {
        if (!Settings.sportOverlayEnabled || !isSportingActivity(activity)) return
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            val decor = activity.window?.decorView as? ViewGroup ?: return@runOnUiThread
            val existing = synchronized(bubbles) { bubbles[activity] }
            if (existing?.parent != null) {
                updateBubble(existing)
                return@runOnUiThread
            }

            val size = dp(activity, 58)
            val margin = dp(activity, 14)
            val bubble = TextView(activity).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setLines(2)
                includeFontPadding = false
                elevation = dp(activity, 8).toFloat()
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(211, 47, 47))
                    setStroke(dp(activity, 2), Color.argb(210, 255, 255, 255))
                }
                contentDescription = "PulseRelay 快捷入口"
            }
            updateBubble(bubble)
            val lp = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(activity, 104)
                marginEnd = margin
            }
            decor.addView(bubble, lp)
            synchronized(bubbles) { bubbles[activity] = bubble }
            Diagnostics.overlayActivity = activity.javaClass.simpleName
            installDrag(activity, decor, bubble)
            bubble.post {
                // Convert gravity placement to explicit coordinates so dragging is predictable.
                bubble.x = (decor.width - bubble.width - margin).coerceAtLeast(0).toFloat()
                bubble.y = dp(activity, 104).toFloat()
            }
            Log.d("Sport overlay attached: ${activity.javaClass.name}")
        }
    }

    fun detach(activity: Activity) {
        activity.runOnUiThread {
            val bubble = synchronized(bubbles) { bubbles.remove(activity) } ?: return@runOnUiThread
            (bubble.parent as? ViewGroup)?.removeView(bubble)
            if (Diagnostics.overlayActivity == activity.javaClass.simpleName) Diagnostics.overlayActivity = "-"
        }
    }

    fun requestRefresh() {
        handler.post {
            val current = synchronized(bubbles) { bubbles.values.toList() }
            current.forEach(::updateBubble)
        }
    }

    private fun updateBubble(view: TextView) {
        val bpm = Diagnostics.lastBpm
        view.text = if (bpm in 20..260) "♥\n$bpm" else "♥\n--"
        view.alpha = if (Settings.enabled) 1f else 0.58f
    }

    private fun installDrag(activity: Activity, parent: ViewGroup, bubble: TextView) {
        val slop = dp(activity, 8).toFloat()
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragged = false

        bubble.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = v.x
                    startY = v.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > slop || abs(dy) > slop) dragged = true
                    val maxX = (parent.width - v.width).coerceAtLeast(0).toFloat()
                    val maxY = (parent.height - v.height).coerceAtLeast(0).toFloat()
                    v.x = (startX + dx).coerceIn(0f, maxX)
                    v.y = (startY + dy).coerceIn(0f, maxY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) SportQuickPanel.show(activity)
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun dp(view: View, value: Int): Int = (view.resources.displayMetrics.density * value).toInt()
    private fun dp(activity: Activity, value: Int): Int = (activity.resources.displayMetrics.density * value).toInt()
}
