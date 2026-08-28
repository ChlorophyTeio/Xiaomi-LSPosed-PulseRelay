package website.xihan.pbra

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

object SportQuickPanel {
    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dark = (activity.resources.configuration.uiMode and 0x30) == 0x20
        val panelBg = if (dark) Color.rgb(28, 28, 30) else Color.WHITE
        val surface = if (dark) Color.rgb(43, 43, 46) else Color.rgb(246, 247, 249)
        val textColor = if (dark) Color.WHITE else Color.rgb(28, 28, 30)
        val secondary = if (dark) Color.rgb(190, 190, 196) else Color.rgb(100, 100, 106)
        val border = if (dark) Color.rgb(70, 70, 74) else Color.rgb(226, 228, 233)
        val accent = Color.rgb(211, 47, 47)
        val positive = Color.rgb(22, 163, 74)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 14))
            background = rounded(panelBg, 24, border)
        }

        val title = TextView(activity).apply {
            text = "♥  PulseRelay"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
        }
        val subtitle = TextView(activity).apply {
            text = "运动中快捷面板"
            textSize = 13f
            setTextColor(secondary)
            setPadding(0, dp(activity, 4), 0, dp(activity, 14))
        }

        val readingCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14))
            background = rounded(surface, 18, border)
        }
        val reading = TextView(activity).apply {
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
        }
        val detail = TextView(activity).apply {
            textSize = 12.5f
            setTextColor(secondary)
            setPadding(0, dp(activity, 7), 0, 0)
            setLineSpacing(dp(activity, 2).toFloat(), 1f)
        }
        readingCard.addView(reading, matchWrap())
        readingCard.addView(detail, matchWrap())

        val enabled = Switch(activity).apply {
            text = "启用实时 UDP 中继"
            isChecked = Settings.enabled
            textSize = 15f
            setTextColor(textColor)
            gravity = Gravity.CENTER_VERTICAL
            thumbTintList = ColorStateList.valueOf(accent)
            setPadding(dp(activity, 4), dp(activity, 12), dp(activity, 4), dp(activity, 10))
        }

        val actionTitle = TextView(activity).apply {
            text = "快捷操作"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(secondary)
            setPadding(0, dp(activity, 4), 0, dp(activity, 8))
        }

        val actionRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val test = Button(activity).apply {
            text = "发送测试心率\n123 BPM"
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(activity, 76)
            background = rounded(accent, 18, accent)
        }
        val full = Button(activity).apply {
            text = "打开完整设置\n目标 / 广播 / 高级"
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            minHeight = dp(activity, 76)
            background = rounded(surface, 18, border)
        }

        val leftLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val rightLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(activity, 10)
        }
        actionRow.addView(test, leftLp)
        actionRow.addView(full, rightLp)

        val statusRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(activity, 12), 0, 0)
        }
        val sourceChip = chip(activity, "来源", surface, textColor, border)
        val targetChip = chip(activity, "目标", surface, textColor, border)
        val stateChip = chip(activity, "状态", surface, positive, border)
        statusRow.addView(sourceChip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusRow.addView(targetChip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(activity, 8)
        })
        statusRow.addView(stateChip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(activity, 8)
        })

        val close = Button(activity).apply {
            text = "返回运动"
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(28, 28, 30))
            minHeight = dp(activity, 50)
            background = rounded(Color.WHITE, 25, border)
        }

        root.addView(title, matchWrap())
        root.addView(subtitle, matchWrap())
        root.addView(readingCard, matchWrap())
        root.addView(enabled, matchWrap())
        root.addView(actionTitle, matchWrap())
        root.addView(actionRow, matchWrap())
        root.addView(statusRow, matchWrap())
        root.addView(space(activity, 12), matchWrap())
        root.addView(close, matchWrap())

        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(root, matchWrap())
        }

        fun refresh() {
            val bpm = if (Diagnostics.lastBpm > 0) Diagnostics.lastBpm.toString() else "--"
            reading.text = "$bpm BPM  ·  ${Diagnostics.currentSourceLabel()}"
            val network = if (Diagnostics.lastStatus.isBlank() || Diagnostics.lastStatus == "-") {
                "暂无网络动作"
            } else {
                "${Diagnostics.lastStatus} · ${Diagnostics.lastResolvedIp}"
            }
            detail.text = "最后更新：${Diagnostics.lastHookAgeText()}\n发送：${Diagnostics.sendOk.get()} 成功 / ${Diagnostics.sendFail.get()} 失败\n最近网络：$network"
            sourceChip.text = "来源\n${Diagnostics.currentSourceLabel()}"
            targetChip.text = "目标\n${Settings.deliveryTargets().size} 路"
            stateChip.text = "状态\n${if (Settings.enabled) "已启用" else "已暂停"}"
            stateChip.setTextColor(if (Settings.enabled) positive else secondary)
        }
        refresh()

        val handler = Handler(Looper.getMainLooper())
        val ticker = object : Runnable {
            override fun run() {
                refresh()
                handler.postDelayed(this, 1000L)
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(scroll)
            .create()

        enabled.setOnCheckedChangeListener { _, checked ->
            Settings.enabled = checked
            SportOverlay.requestRefresh()
            refresh()
        }
        test.setOnClickListener {
            test.isEnabled = false
            val oldText = test.text
            test.text = "发送中…"
            HeartRateBridge.sendTestAll(
                Settings.endpointsText,
                Settings.broadcastEnabled,
                Settings.broadcastPort,
                123
            ) { results ->
                test.isEnabled = true
                test.text = oldText
                val ok = results.count { it.second.ok }
                Toast.makeText(activity, "UDP 测试包已发送：$ok/${results.size}", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
        full.setOnClickListener {
            dialog.dismiss()
            ConfigDialog.show(activity)
        }
        close.setOnClickListener { dialog.dismiss() }

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(rounded(Color.TRANSPARENT, 0, Color.TRANSPARENT))
            handler.post(ticker)
        }
        dialog.setOnDismissListener { handler.removeCallbacks(ticker) }
        dialog.show()
    }

    private fun chip(
        activity: Activity,
        title: String,
        backgroundColor: Int,
        textColor: Int,
        strokeColor: Int
    ): TextView = TextView(activity).apply {
        text = title
        gravity = Gravity.CENTER
        textSize = 12.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        setPadding(dp(activity, 8), dp(activity, 10), dp(activity, 8), dp(activity, 10))
        background = rounded(backgroundColor, 15, strokeColor)
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp.toFloat()
            setColor(color)
            if (strokeColor != Color.TRANSPARENT) setStroke(1, strokeColor)
        }

    private fun space(activity: Activity, heightDp: Int): View = View(activity).apply {
        minimumHeight = dp(activity, heightDp)
    }

    private fun dp(activity: Activity, value: Int): Int = (activity.resources.displayMetrics.density * value).toInt()
    private fun matchWrap() = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
