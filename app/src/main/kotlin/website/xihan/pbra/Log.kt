package website.xihan.pbra

import android.util.Log as AndroidLog
import de.robv.android.xposed.XposedBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object Log {
    private const val TAG = "PulseRelay"
    private const val MAX_LINES = 160
    private val lines = ConcurrentLinkedDeque<String>()

    private fun append(level: String, message: String, forceXposed: Boolean) {
        val line = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} $level $message"
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.pollFirst()

        when (level) {
            "E" -> AndroidLog.e(TAG, message)
            "W" -> AndroidLog.w(TAG, message)
            "I" -> AndroidLog.i(TAG, message)
            else -> AndroidLog.d(TAG, message)
        }

        if (forceXposed || Settings.debugEnabled) {
            runCatching { XposedBridge.log("$TAG: $line") }
        }
    }

    fun d(message: Any?) {
        if (Settings.debugEnabled) append("D", message.toString(), false)
    }

    fun i(message: Any?) = append("I", message.toString(), true)
    fun w(message: Any?) = append("W", message.toString(), true)

    fun e(message: Any?) {
        val text = if (message is Throwable) AndroidLog.getStackTraceString(message) else message.toString()
        append("E", text, true)
    }

    fun tail(max: Int = 40): String = lines.toList().takeLast(max).joinToString("\n")
}
