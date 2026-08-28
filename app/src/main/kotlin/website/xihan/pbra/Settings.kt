package website.xihan.pbra

import android.content.Context

object Settings {
    // Keep the old preference file so v2.x users retain their existing target list and switches.
    private const val PREFS = "heart_rate_obs_bridge_v2"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ENDPOINT = "endpoint" // legacy single-target key
    private const val KEY_ENDPOINTS = "endpoints"
    private const val KEY_DEBUG = "debug"
    private const val KEY_FORCE_WIFI = "force_wifi"
    private const val KEY_DEDUPE_WINDOW = "dedupe_window_ms"
    private const val KEY_SPORT_OVERLAY = "sport_overlay_enabled"
    private const val KEY_BROADCAST_ENABLED = "udp_broadcast_enabled"
    private const val KEY_BROADCAST_PORT = "udp_broadcast_port"
    private const val KEY_PING_TIMEOUT = "udp_ping_timeout_ms"

    const val DEFAULT_PORT = 18181
    const val MAX_TARGETS = 8

    private val prefs get() = AppContext.application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = if (AppContext.isReady()) prefs.getBoolean(KEY_ENABLED, true) else true
        set(value) { if (AppContext.isReady()) prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    var endpointsText: String
        get() {
            if (!AppContext.isReady()) return ""
            val modern = prefs.getString(KEY_ENDPOINTS, null)
            if (!modern.isNullOrBlank()) return migrateLegacyTargets(modern)
            return migrateLegacyTargets(prefs.getString(KEY_ENDPOINT, "") ?: "")
        }
        set(value) {
            if (!AppContext.isReady()) return
            val normalized = parseTargets(value).joinToString("\n")
            prefs.edit()
                .putString(KEY_ENDPOINTS, normalized)
                .putString(KEY_ENDPOINT, parseTargets(normalized).firstOrNull().orEmpty())
                .apply()
        }

    /** Legacy alias kept for old configuration migration. */
    var endpoint: String
        get() = targets().firstOrNull().orEmpty()
        set(value) { endpointsText = value }

    fun targets(): List<String> = parseTargets(endpointsText)

    fun parseTargets(raw: String): List<String> = raw
        .replace(';', '\n')
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith('#') }
        .mapNotNull { PulseUdp.normalizeTarget(it).getOrNull() }
        .distinct()
        .take(MAX_TARGETS)
        .toList()

    /**
     * v2.x stored HTTP endpoints. v3 is UDP-only, so strip scheme/path automatically.
     * Example: http://192.168.1.10:18181/receive_data -> 192.168.1.10:18181
     * Domain targets such as obs.example.com:18181 are preserved and resolved by PulseUdp.
     */
    private fun migrateLegacyTargets(raw: String): String = raw
        .replace(';', '\n')
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith('#') }
        .mapNotNull { PulseUdp.normalizeTarget(it).getOrNull() }
        .distinct()
        .take(MAX_TARGETS)
        .joinToString("\n")

    fun deliveryTargets(): List<String> = buildList {
        addAll(targets())
        if (broadcastEnabled) add("broadcast:${broadcastPort}")
    }.distinct()

    var debugEnabled: Boolean
        get() = if (AppContext.isReady()) prefs.getBoolean(KEY_DEBUG, true) else true
        set(value) { if (AppContext.isReady()) prefs.edit().putBoolean(KEY_DEBUG, value).apply() }

    var forceWifi: Boolean
        get() = if (AppContext.isReady()) prefs.getBoolean(KEY_FORCE_WIFI, true) else true
        set(value) { if (AppContext.isReady()) prefs.edit().putBoolean(KEY_FORCE_WIFI, value).apply() }

    var sportOverlayEnabled: Boolean
        get() = if (AppContext.isReady()) prefs.getBoolean(KEY_SPORT_OVERLAY, true) else true
        set(value) { if (AppContext.isReady()) prefs.edit().putBoolean(KEY_SPORT_OVERLAY, value).apply() }

    var broadcastEnabled: Boolean
        get() = if (AppContext.isReady()) prefs.getBoolean(KEY_BROADCAST_ENABLED, false) else false
        set(value) { if (AppContext.isReady()) prefs.edit().putBoolean(KEY_BROADCAST_ENABLED, value).apply() }

    var broadcastPort: Int
        get() = if (AppContext.isReady()) prefs.getInt(KEY_BROADCAST_PORT, DEFAULT_PORT).coerceIn(1, 65535) else DEFAULT_PORT
        set(value) { if (AppContext.isReady()) prefs.edit().putInt(KEY_BROADCAST_PORT, value.coerceIn(1, 65535)).apply() }

    var pingTimeoutMs: Int
        get() = if (AppContext.isReady()) prefs.getInt(KEY_PING_TIMEOUT, 900).coerceIn(200, 5000) else 900
        set(value) { if (AppContext.isReady()) prefs.edit().putInt(KEY_PING_TIMEOUT, value.coerceIn(200, 5000)).apply() }

    var dedupeWindowMs: Long
        get() = if (AppContext.isReady()) prefs.getLong(KEY_DEDUPE_WINDOW, 300L).coerceIn(0L, 5000L) else 300L
        set(value) { if (AppContext.isReady()) prefs.edit().putLong(KEY_DEDUPE_WINDOW, value.coerceIn(0L, 5000L)).apply() }
}
