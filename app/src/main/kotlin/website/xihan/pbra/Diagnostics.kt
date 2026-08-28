package website.xihan.pbra

import android.os.Build
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object Diagnostics {
    data class TargetState(
        @Volatile var ok: Long = 0,
        @Volatile var fail: Long = 0,
        @Volatile var lastAt: Long = 0,
        @Volatile var lastStatus: String = "-",
        @Volatile var resolvedIp: String = "-",
        @Volatile var lastError: String = ""
    )

    val hookCallbacks = AtomicLong(0)
    val duplicateDrops = AtomicLong(0)
    val sendOk = AtomicLong(0)
    val sendFail = AtomicLong(0)
    val hookInstalled = AtomicInteger(0)
    val targetStates = ConcurrentHashMap<String, TargetState>()

    @Volatile var processName: String = ""
    @Volatile var lastHookSource: String = "-"
    @Volatile var lastBpm: Int = 0
    @Volatile var lastHookAt: Long = 0
    @Volatile var lastSendAt: Long = 0
    @Volatile var lastStatus: String = "-"
    @Volatile var lastResolvedIp: String = "-"
    @Volatile var lastError: String = ""
    @Volatile var overlayActivity: String = "-"

    fun onHeartRate(bpm: Int, source: String) {
        hookCallbacks.incrementAndGet()
        lastBpm = bpm
        lastHookSource = source
        lastHookAt = System.currentTimeMillis()
    }

    fun recordTarget(target: String, result: PulseUdp.Result) {
        val state = targetStates.computeIfAbsent(target) { TargetState() }
        synchronized(state) {
            if (result.ok) state.ok++ else state.fail++
            state.lastAt = System.currentTimeMillis()
            state.lastStatus = result.status.ifBlank { result.error }
            state.resolvedIp = result.resolvedIp
            state.lastError = result.error
        }
        lastSendAt = state.lastAt
        lastStatus = state.lastStatus
        lastResolvedIp = result.resolvedIp
        lastError = result.error
        if (result.ok) sendOk.incrementAndGet() else sendFail.incrementAndGet()
    }

    fun currentSourceLabel(): String = when {
        lastHookAt <= 0L -> "无数据"
        lastHookSource.contains("DailyHrReport", ignoreCase = true) -> "日常心率"
        lastHookSource.contains("Sport", ignoreCase = true) ||
            lastHookSource.contains("BaseSportVM", ignoreCase = true) ||
            lastHookSource.contains("CommonSportModel", ignoreCase = true) -> "运动实时"
        else -> "实时心率"
    }

    fun lastHookAgeText(now: Long = System.currentTimeMillis()): String {
        if (lastHookAt <= 0L) return "尚未收到"
        val delta = (now - lastHookAt).coerceAtLeast(0L)
        return when {
            delta < 1_000L -> "刚刚"
            delta < 60_000L -> "${delta / 1_000L} 秒前"
            delta < 3_600_000L -> "${delta / 60_000L} 分钟前"
            else -> "${delta / 3_600_000L} 小时前"
        }
    }

    fun currentReadingText(now: Long = System.currentTimeMillis()): String = buildString {
        append("来源：").append(currentSourceLabel())
        append("  ·  BPM：").append(if (lastBpm > 0) lastBpm else "--")
        append("  ·  更新：").append(lastHookAgeText(now))
    }

    fun targetSummary(): String {
        val targets = Settings.deliveryTargets()
        if (targets.isEmpty()) return "未配置目标"
        return targets.mapIndexed { index, target ->
            val state = targetStates[target]
            if (state == null) {
                "${index + 1}. $target · 未测试"
            } else {
                val marker = if (state.lastError.isBlank()) "OK" else "ERR"
                "${index + 1}. $target · $marker · ${state.ok}/${state.fail} · ${state.lastStatus}"
            }
        }.joinToString("\n")
    }

    fun localAddresses(): String = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .flatMap { Collections.list(it.inetAddresses) }
            .filter { !it.isLoopbackAddress }
            .joinToString(", ") { it.hostAddress ?: "?" }
            .ifBlank { "-" }
    }.getOrDefault("-")

    private fun time(value: Long): String = if (value <= 0) "-" else
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(value))

    fun snapshot(): String = buildString {
        appendLine("PulseRelay 3.0.0 / UDP PULSE/1")
        appendLine("Android: ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.BRAND} ${Build.MODEL}")
        appendLine("Process: $processName")
        appendLine("Enabled: ${Settings.enabled}")
        appendLine("Targets: ${Settings.targets().size}/${Settings.MAX_TARGETS}")
        Settings.targets().forEachIndexed { i, target -> appendLine("  [${i + 1}] $target") }
        appendLine("Broadcast: ${Settings.broadcastEnabled} :${Settings.broadcastPort}")
        appendLine("Force Wi-Fi: ${Settings.forceWifi}")
        appendLine("Sport overlay: ${Settings.sportOverlayEnabled} @ $overlayActivity")
        appendLine("Local IP: ${localAddresses()}")
        appendLine("Hooks installed: ${hookInstalled.get()}")
        appendLine("Hook callbacks: ${hookCallbacks.get()}")
        appendLine("Duplicate drops: ${duplicateDrops.get()}")
        appendLine("Last BPM: $lastBpm")
        appendLine("Last source: $lastHookSource")
        appendLine("Last hook: ${time(lastHookAt)}")
        appendLine("UDP send/test OK/Fail: ${sendOk.get()}/${sendFail.get()}")
        appendLine("Last network action: ${time(lastSendAt)}")
        appendLine("Last status: $lastStatus")
        appendLine("Resolved IP: $lastResolvedIp")
        appendLine("Last error: ${lastError.ifBlank { "-" }}")
        appendLine("--- Targets ---")
        append(targetSummary())
    }
}
