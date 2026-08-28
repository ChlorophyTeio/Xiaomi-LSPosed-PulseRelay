package website.xihan.pbra

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

object ConfigDialog {
    private data class Palette(
        val background: Int,
        val surface: Int,
        val surfaceAlt: Int,
        val text: Int,
        val textSecondary: Int,
        val border: Int,
        val accent: Int,
        val accentSoft: Int,
        val success: Int,
        val warning: Int,
        val danger: Int
    )

    fun show(activity: Activity) {
        val p = palette(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 22))
            setBackgroundColor(p.background)
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(p.background)
            addView(root)
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(activity).apply {
            text = "♥"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(211, 47, 47))
            }
        }, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)).apply {
            marginEnd = dp(activity, 12)
        })
        header.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                text = "PulseRelay"
                textSize = 21f
                setTextColor(p.text)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(activity).apply {
                text = "v3.1 · 小米运动健康 3.58.x · UDP 心率中继"
                textSize = 12.5f
                setTextColor(p.textSecondary)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header, matchWrap())
        addGap(root, activity, 14)

        val endpoints = EditText(activity).apply {
            hint = "每行一个接收端，支持 IP / 域名。端口省略时默认 ${Settings.DEFAULT_PORT}：\n192.168.1.10\nobs.example.com:18181"
            setText(Settings.endpointsText)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isSingleLine = false
            minLines = 3
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            textSize = 15f
            setTextColor(p.text)
            setHintTextColor(p.textSecondary)
            background = rounded(activity, p.surfaceAlt, p.border, 10, 1)
            setPadding(dp(activity, 12), dp(activity, 11), dp(activity, 12), dp(activity, 11))
        }
        val targetCount = TextView(activity).apply {
            textSize = 12f
            setTextColor(p.textSecondary)
            setPadding(0, dp(activity, 5), 0, 0)
        }
        fun refreshTargetCount() {
            val uni = Settings.parseTargets(endpoints.text.toString()).size
            val extra = if (Settings.broadcastEnabled) " + 广播" else ""
            targetCount.text = "当前 $uni/${Settings.MAX_TARGETS} 个单播目标$extra · 支持 IP / 域名 / 主机名"
        }

        val enabled = highContrastSwitch(activity, "启用实时中继", Settings.enabled, p)
        val forceWifi = highContrastSwitch(activity, "局域网 UDP 优先走 Wi-Fi", Settings.forceWifi, p)
        val broadcast = highContrastSwitch(activity, "同时发送局域网广播（同网段多个接收端可零配置接收）", Settings.broadcastEnabled, p)
        val broadcastPort = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Settings.broadcastPort.toString())
            isSingleLine = true
            textSize = 15f
            setTextColor(p.text)
            hint = Settings.DEFAULT_PORT.toString()
            setHintTextColor(p.textSecondary)
            background = rounded(activity, p.surfaceAlt, p.border, 9, 1)
            setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9))
        }
        val sportOverlay = highContrastSwitch(activity, "运动页面显示快捷悬浮按钮", Settings.sportOverlayEnabled, p)
        val debug = highContrastSwitch(activity, "详细调试日志（LSPosed）", Settings.debugEnabled, p)

        addCard(root, activity, p, "UDP 接收端", "支持 IPv4、IPv6 和域名。域名会自动 DNS 解析并在诊断中显示实际 IP；广播适合同网段多个接收端。") { box ->
            box.addView(endpoints, matchWrap())
            box.addView(targetCount, matchWrap())
            addGap(box, activity, 8)
            box.addView(enabled)
            box.addView(forceWifi)
            box.addView(broadcast)
            box.addView(TextView(activity).apply {
                text = "广播端口"
                textSize = 13f
                setTextColor(p.textSecondary)
                setPadding(0, dp(activity, 7), 0, dp(activity, 4))
            })
            box.addView(broadcastPort, matchWrap())
        }

        addCard(root, activity, p, "运动页面入口", "快捷按钮直接注入运动 Activity，不需要系统悬浮窗权限。拖动可移动，点击打开快捷面板。") { box ->
            box.addView(sportOverlay)
        }

        val liveSource = TextView(activity).apply {
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(p.text)
        }
        val liveMeta = TextView(activity).apply {
            textSize = 14f
            setTextColor(p.textSecondary)
            setPadding(0, dp(activity, 4), 0, 0)
        }
        val liveRaw = TextView(activity).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(p.textSecondary)
            setPadding(0, dp(activity, 6), 0, 0)
        }
        fun refreshLiveReading() {
            val source = Diagnostics.currentSourceLabel()
            val bpm = Diagnostics.lastBpm
            liveSource.text = when (source) {
                "运动实时" -> "● 运动实时"
                "日常心率" -> "● 日常心率"
                "无数据" -> "○ 暂无心率数据"
                else -> "● $source"
            }
            liveSource.setTextColor(when (source) {
                "运动实时" -> p.success
                "日常心率" -> p.warning
                "无数据" -> p.textSecondary
                else -> p.accent
            })
            liveMeta.text = "当前 BPM：${if (bpm > 0) bpm else "--"}    最后更新：${Diagnostics.lastHookAgeText()}"
            liveRaw.text = "Hook：${Diagnostics.lastHookSource}    UDP 输出：${Settings.deliveryTargets().size} 路"
        }
        addCard(root, activity, p, "当前数据", "实时查看数据来源、BPM 与最后更新时间。") { box ->
            box.addView(liveSource, matchWrap())
            box.addView(liveMeta, matchWrap())
            box.addView(liveRaw, matchWrap())
        }
        refreshLiveReading()

        val resultText = TextView(activity).apply {
            text = "尚未执行 PING/PONG 测试"
            textSize = 13f
            setTextColor(p.textSecondary)
            setPadding(dp(activity, 2), dp(activity, 8), dp(activity, 2), 0)
        }

        fun draftBroadcastPort(): Int = broadcastPort.text.toString().toIntOrNull()?.coerceIn(1, 65535) ?: Settings.DEFAULT_PORT
        fun applyDraft(): String {
            val raw = endpoints.text.toString().trim()
            Settings.endpointsText = raw
            Settings.enabled = enabled.isChecked
            Settings.debugEnabled = debug.isChecked
            Settings.forceWifi = forceWifi.isChecked
            Settings.sportOverlayEnabled = sportOverlay.isChecked
            Settings.broadcastEnabled = broadcast.isChecked
            Settings.broadcastPort = draftBroadcastPort()
            refreshTargetCount()
            if (SportOverlay.isSportingActivity(activity)) SportOverlay.sync(activity)
            return raw
        }

        val testPing = actionButton(activity, "PING 全部接收端", p, primary = true)
        val testBpm = actionButton(activity, "发送测试心率 123 BPM", p, primary = false)
        addCard(root, activity, p, "快速测试", "PING 会等待 OBS UDP 接收器返回 PONG；广播模式会统计同网段返回 PONG 的接收器数量。") { box ->
            box.addView(testPing, matchWrap())
            addGap(box, activity, 8)
            box.addView(testBpm, matchWrap())
            box.addView(resultText, matchWrap())
        }

        val advancedContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = rounded(activity, p.surface, p.border, 12, 1)
            setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 4))
        }
        val advancedToggle = actionButton(activity, "高级设置  ▾", p, primary = false).apply {
            setOnClickListener {
                val open = advancedContainer.visibility != View.VISIBLE
                advancedContainer.visibility = if (open) View.VISIBLE else View.GONE
                text = if (open) "高级设置  ▴" else "高级设置  ▾"
            }
        }
        root.addView(advancedToggle, matchWrap())
        addGap(root, activity, 8)
        root.addView(advancedContainer, matchWrap())

        fun numberField(title: String, subtitle: String, current: Number): EditText {
            advancedContainer.addView(TextView(activity).apply {
                text = title
                textSize = 14f
                setTextColor(p.text)
                typeface = Typeface.DEFAULT_BOLD
            })
            advancedContainer.addView(TextView(activity).apply {
                text = subtitle
                textSize = 12f
                setTextColor(p.textSecondary)
                setPadding(0, dp(activity, 2), 0, dp(activity, 5))
            })
            return EditText(activity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(current.toString())
                isSingleLine = true
                textSize = 15f
                setTextColor(p.text)
                background = rounded(activity, p.surfaceAlt, p.border, 9, 1)
                setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9))
                advancedContainer.addView(this, matchWrap())
                addGap(advancedContainer, activity, 10)
            }
        }
        advancedContainer.addView(debug)
        addGap(advancedContainer, activity, 10)
        val pingTimeout = numberField("PING 等待时间", "200–5000 ms；只影响连接测试，不影响实时心率", Settings.pingTimeoutMs)
        val dedupeWindow = numberField("相同 BPM 去重窗口", "0–5000 ms；建议 300，降低重复 UDP 包", Settings.dedupeWindowMs)
        fun applyAdvanced() {
            Settings.pingTimeoutMs = pingTimeout.text.toString().toIntOrNull() ?: 900
            Settings.dedupeWindowMs = dedupeWindow.text.toString().toLongOrNull() ?: 300L
        }

        val status = TextView(activity).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(p.text)
            setTextIsSelectable(true)
            text = Diagnostics.snapshot()
            background = rounded(activity, p.surfaceAlt, p.border, 10, 1)
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
        }
        val refreshStatus = actionButton(activity, "刷新状态", p, primary = false)
        val copyStatus = actionButton(activity, "复制诊断信息", p, primary = false)
        addCard(root, activity, p, "诊断状态", "UDP 实时发送不等待确认；请用上面的 PING/PONG 判断某个接收端是否真的在线。") { box ->
            box.addView(status, matchWrap())
            addGap(box, activity, 8)
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(refreshStatus, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(activity, 4) })
            row.addView(copyStatus, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(activity, 4) })
            box.addView(row, matchWrap())
        }

        fun formatResults(results: List<Pair<String, PulseUdp.Result>>): String {
            if (results.isEmpty()) return "没有可测试的目标"
            return results.joinToString("\n") { (target, result) ->
                if (result.ok) "✓ $target · ${result.status} · ${result.durationMs}ms · ${result.resolvedIp}"
                else "✗ $target · ${result.error}"
            }
        }

        testPing.setOnClickListener {
            val raw = applyDraft()
            applyAdvanced()
            val useBroadcast = broadcast.isChecked
            val bPort = draftBroadcastPort()
            setBusy(testPing, true, "PING 中…", "PING 全部接收端")
            HeartRateBridge.testConnections(raw, useBroadcast, bPort) { results ->
                setBusy(testPing, false, "PING 中…", "PING 全部接收端")
                val ok = results.count { it.second.ok }
                resultText.setTextColor(if (results.isNotEmpty() && ok == results.size) p.success else p.danger)
                resultText.text = "PONG：$ok/${results.size} 有响应\n${formatResults(results)}"
                status.text = Diagnostics.snapshot()
            }
        }

        testBpm.setOnClickListener {
            val raw = applyDraft()
            applyAdvanced()
            val useBroadcast = broadcast.isChecked
            val bPort = draftBroadcastPort()
            setBusy(testBpm, true, "发送中…", "发送测试心率 123 BPM")
            HeartRateBridge.sendTestAll(raw, useBroadcast, bPort, 123) { results ->
                setBusy(testBpm, false, "发送中…", "发送测试心率 123 BPM")
                val ok = results.count { it.second.ok }
                resultText.setTextColor(if (results.isNotEmpty() && ok == results.size) p.success else p.danger)
                resultText.text = "UDP 发送：$ok/${results.size} 已交给网络栈\n${formatResults(results)}\n提示：UDP SENT 不代表对端收到；确认请点 PING。"
                status.text = Diagnostics.snapshot()
            }
        }

        refreshStatus.setOnClickListener {
            refreshLiveReading()
            refreshTargetCount()
            status.text = Diagnostics.snapshot()
            resultText.setTextColor(p.textSecondary)
            resultText.text = "状态已刷新"
        }
        copyStatus.setOnClickListener {
            val report = Diagnostics.snapshot() + "\n--- Recent logs ---\n" + Log.tail(60)
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("PulseRelay diagnostics", report))
            Toast.makeText(activity, "诊断信息已复制", Toast.LENGTH_SHORT).show()
        }

        val liveHandler = Handler(Looper.getMainLooper())
        val liveTicker = object : Runnable {
            override fun run() {
                refreshLiveReading()
                refreshTargetCount()
                liveHandler.postDelayed(this, 1000L)
            }
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                applyDraft()
                applyAdvanced()
                Toast.makeText(activity, "PulseRelay 设置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .create()
        dialog.setOnShowListener {
            liveHandler.post(liveTicker)
            dialog.window?.setBackgroundDrawable(ColorDrawable(p.background))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                setTextColor(p.accent); textSize = 15f; isAllCaps = false; typeface = Typeface.DEFAULT_BOLD
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
                setTextColor(p.textSecondary); textSize = 15f; isAllCaps = false
            }
        }
        dialog.setOnDismissListener { liveHandler.removeCallbacks(liveTicker) }
        dialog.show()
    }

    private fun addCard(root: LinearLayout, activity: Activity, p: Palette, title: String, subtitle: String, content: (LinearLayout) -> Unit) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 13), dp(activity, 14), dp(activity, 13))
            background = rounded(activity, p.surface, p.border, 12, 1)
        }
        card.addView(TextView(activity).apply {
            text = title; textSize = 17f; setTextColor(p.text); typeface = Typeface.DEFAULT_BOLD
        })
        card.addView(TextView(activity).apply {
            text = subtitle; textSize = 12.5f; setTextColor(p.textSecondary); setPadding(0, dp(activity, 3), 0, dp(activity, 10))
        })
        content(card)
        root.addView(card, matchWrap())
        addGap(root, activity, 12)
    }

    private fun highContrastSwitch(activity: Activity, label: String, checked: Boolean, p: Palette): Switch {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        return Switch(activity).apply {
            text = label; isChecked = checked; textSize = 15f; setTextColor(p.text); gravity = Gravity.CENTER_VERTICAL
            thumbTintList = ColorStateList(states, intArrayOf(p.accent, Color.rgb(158, 158, 158)))
            trackTintList = ColorStateList(states, intArrayOf(p.accentSoft, Color.rgb(100, 100, 100)))
            setPadding(0, dp(activity, 3), 0, dp(activity, 3))
        }
    }

    private fun actionButton(activity: Activity, label: String, p: Palette, primary: Boolean): Button = Button(activity).apply {
        text = label; textSize = 14.5f; isAllCaps = false; typeface = Typeface.DEFAULT_BOLD; minHeight = dp(activity, 46)
        setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8))
        if (primary) {
            setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(p.accent)
        } else {
            setTextColor(p.text); background = rounded(activity, p.surfaceAlt, p.border, 10, 1)
        }
    }

    private fun setBusy(button: Button, busy: Boolean, busyText: String, idleText: String) {
        button.isEnabled = !busy; button.text = if (busy) busyText else idleText; button.alpha = if (busy) 0.68f else 1f
    }
    private fun addGap(layout: LinearLayout, context: Context, value: Int) {
        layout.addView(View(context), LinearLayout.LayoutParams(1, dp(context, value)))
    }
    private fun rounded(context: Context, fill: Int, stroke: Int, radiusDp: Int, strokeDp: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(context, radiusDp).toFloat(); setStroke(dp(context, strokeDp).coerceAtLeast(1), stroke)
    }
    private fun palette(context: Context): Palette {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (dark) Palette(
            Color.rgb(18,18,18), Color.rgb(30,30,30), Color.rgb(40,40,40), Color.rgb(245,245,245), Color.rgb(190,190,190),
            Color.rgb(68,68,68), Color.rgb(211,47,47), Color.rgb(190,90,90), Color.rgb(105,240,174), Color.rgb(255,213,79), Color.rgb(255,138,128)
        ) else Palette(
            Color.rgb(250,250,250), Color.WHITE, Color.rgb(245,247,249), Color.rgb(28,28,30), Color.rgb(92,92,98),
            Color.rgb(218,220,224), Color.rgb(198,40,40), Color.rgb(239,154,154), Color.rgb(27,122,67), Color.rgb(166,104,0), Color.rgb(183,28,28)
        )
    }
    private fun dp(context: Context, value: Int): Int = (context.resources.displayMetrics.density * value).toInt()
    private fun matchWrap() = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
