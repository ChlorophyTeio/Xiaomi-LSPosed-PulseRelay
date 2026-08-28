package website.xihan.pbra

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object HeartRateBridge {
    private data class Sample(val bpm: Int, val source: String, val measuredAt: Long, val seq: Long)

    private val networkExecutor = Executors.newSingleThreadExecutor(ThreadFactory { r ->
        Thread(r, "PulseRelay-UDP").apply { isDaemon = true }
    })
    private val testExecutor = Executors.newSingleThreadExecutor(ThreadFactory { r ->
        Thread(r, "PulseRelay-Test").apply { isDaemon = true }
    })
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = AtomicReference<Sample?>(null)
    private val draining = AtomicBoolean(false)
    private val sequence = AtomicLong(0)

    @Volatile private var lastQueuedBpm = 0
    @Volatile private var lastQueuedAt = 0L

    fun offer(bpm: Int, source: String) {
        if (bpm !in 20..260) return
        Diagnostics.onHeartRate(bpm, source)
        Log.d("实时心率[$source]: $bpm")
        SportOverlay.requestRefresh()

        if (!Settings.enabled) return
        val targets = Settings.deliveryTargets()
        if (targets.isEmpty()) {
            Diagnostics.lastError = "未配置 UDP 目标，也未启用广播"
            return
        }

        val now = System.currentTimeMillis()
        if (bpm == lastQueuedBpm && now - lastQueuedAt < Settings.dedupeWindowMs) {
            Diagnostics.duplicateDrops.incrementAndGet()
            return
        }
        lastQueuedBpm = bpm
        lastQueuedAt = now

        pending.set(Sample(bpm, source, now, sequence.incrementAndGet()))
        scheduleDrain()
    }

    private fun scheduleDrain() {
        if (!draining.compareAndSet(false, true)) return
        networkExecutor.execute(::drain)
    }

    private fun drain() {
        try {
            while (true) {
                val sample = pending.getAndSet(null) ?: break
                val targets = Settings.deliveryTargets()
                for (target in targets) {
                    val result = PulseUdp.sendHeartRate(target, sample.seq, sample.bpm, sample.measuredAt)
                    Diagnostics.recordTarget(target, result)
                    if (result.ok) {
                        Log.d("UDP 已发送 target=$target BPM=${sample.bpm} seq=${sample.seq} ${result.durationMs}ms @${result.resolvedIp}")
                    } else {
                        Log.e("UDP 发送失败 target=$target BPM=${sample.bpm}: ${result.error}")
                    }
                }
            }
        } finally {
            draining.set(false)
            if (pending.get() != null) scheduleDrain()
        }
    }

    /** PING/PONG connectivity test. Broadcast targets may report multiple receivers. */
    fun testConnections(rawTargets: String, includeBroadcast: Boolean, broadcastPort: Int,
                        callback: (List<Pair<String, PulseUdp.Result>>) -> Unit) {
        val targets = Settings.parseTargets(rawTargets).toMutableList()
        if (includeBroadcast) targets += "broadcast:${broadcastPort.coerceIn(1, 65535)}"
        val unique = targets.distinct()
        testExecutor.execute {
            val results = unique.map { target -> target to PulseUdp.ping(target) }
            results.forEach { (target, result) -> Diagnostics.recordTarget(target, result) }
            mainHandler.post { callback(results) }
        }
    }

    /** Fire-and-forget test packet. Use testConnections() first when you need positive PONG confirmation. */
    fun sendTestAll(rawTargets: String, includeBroadcast: Boolean, broadcastPort: Int, bpm: Int = 123,
                    callback: (List<Pair<String, PulseUdp.Result>>) -> Unit) {
        val targets = Settings.parseTargets(rawTargets).toMutableList()
        if (includeBroadcast) targets += "broadcast:${broadcastPort.coerceIn(1, 65535)}"
        val unique = targets.distinct()
        testExecutor.execute {
            val now = System.currentTimeMillis()
            val seq = sequence.incrementAndGet()
            val results = unique.map { target -> target to PulseUdp.sendHeartRate(target, seq, bpm, now) }
            results.forEach { (target, result) -> Diagnostics.recordTarget(target, result) }
            mainHandler.post { callback(results) }
        }
    }
}
