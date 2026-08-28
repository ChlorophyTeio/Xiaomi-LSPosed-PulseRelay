package website.xihan.pbra

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

/**
 * Tiny UDP transport used by PulseRelay.
 *
 * Protocol (UTF-8 text):
 *   PULSE/1|HR|<seq>|<bpm>|<unix_ms>
 *   PULSE/1|PING|<nonce>
 *   PULSE/1|PONG|<nonce>
 *
 * Targets can be IPv4, IPv6, or DNS host names. DNS is resolved on the selected Wi-Fi network
 * when "force Wi-Fi" is enabled, with a short cache so real-time HR packets do not trigger a
 * DNS lookup on every BPM update.
 */
object PulseUdp {
    private const val MAGIC = "PULSE/1"
    private const val BROADCAST_HOST = "255.255.255.255"
    private const val DNS_CACHE_TTL_MS = 30_000L
    private const val DNS_STALE_FALLBACK_MS = 5 * 60_000L

    data class Result(
        val ok: Boolean,
        val status: String,
        val resolvedIp: String,
        val durationMs: Long,
        val error: String = ""
    )

    private data class Target(
        val display: String,
        val host: String,
        val port: Int,
        val broadcast: Boolean
    )

    private data class DnsCacheEntry(
        val address: InetAddress,
        val resolvedAt: Long
    )

    private val dnsCache = ConcurrentHashMap<String, DnsCacheEntry>()

    fun normalizeTarget(input: String): kotlin.Result<String> = runCatching {
        val target = parseTarget(input)
        if (target.broadcast) "broadcast:${target.port}" else formatHostPort(target.host, target.port)
    }

    fun heartRatePayload(seq: Long, bpm: Int, measuredAt: Long): ByteArray =
        "$MAGIC|HR|$seq|$bpm|$measuredAt".toByteArray(Charsets.UTF_8)

    fun sendHeartRate(targetText: String, seq: Long, bpm: Int, measuredAt: Long): Result {
        if (bpm !in 20..260) return Result(false, "", "-", 0, "BPM 超出范围")
        return sendOnly(targetText, heartRatePayload(seq, bpm, measuredAt))
    }

    fun ping(targetText: String): Result {
        val started = System.currentTimeMillis()
        val target = runCatching { parseTarget(targetText) }.getOrElse {
            return Result(false, "", "-", 0, it.message ?: "目标无效")
        }
        val address = resolve(target).getOrElse {
            return Result(false, "", "-", System.currentTimeMillis() - started, it.message ?: "解析失败")
        }
        val nonce = java.lang.Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36)
        val payload = "$MAGIC|PING|$nonce".toByteArray(Charsets.UTF_8)
        val expected = "$MAGIC|PONG|$nonce"

        return try {
            val socket = createSocketPreferWifi()
            socket.use {
                it.broadcast = target.broadcast
                it.soTimeout = Settings.pingTimeoutMs.coerceAtLeast(200)
                it.send(DatagramPacket(payload, payload.size, address, target.port))

                val deadline = System.currentTimeMillis() + Settings.pingTimeoutMs
                val responders = linkedSetOf<String>()
                val buffer = ByteArray(512)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        it.receive(packet)
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                    val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                    if (text == expected) {
                        responders += packet.address.hostAddress ?: packet.address.hostName
                        if (!target.broadcast) break
                        it.soTimeout = (deadline - System.currentTimeMillis()).coerceAtLeast(1L).toInt()
                    }
                }

                if (responders.isEmpty()) {
                    Result(
                        false,
                        "PING timeout",
                        address.hostAddress ?: target.host,
                        System.currentTimeMillis() - started,
                        "未收到 PONG"
                    )
                } else {
                    val status = if (target.broadcast) "PONG x${responders.size}" else "PONG"
                    Result(true, status, responders.joinToString(","), System.currentTimeMillis() - started)
                }
            }
        } catch (e: Throwable) {
            Result(
                false,
                "",
                address.hostAddress ?: target.host,
                System.currentTimeMillis() - started,
                "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
            )
        }
    }

    private fun sendOnly(targetText: String, payload: ByteArray): Result {
        val started = System.currentTimeMillis()
        val target = runCatching { parseTarget(targetText) }.getOrElse {
            return Result(false, "", "-", 0, it.message ?: "目标无效")
        }
        val address = resolve(target).getOrElse {
            return Result(false, "", "-", System.currentTimeMillis() - started, it.message ?: "解析失败")
        }
        return try {
            val socket = createSocketPreferWifi()
            socket.use {
                it.broadcast = target.broadcast
                it.send(DatagramPacket(payload, payload.size, address, target.port))
            }
            Result(true, "UDP SENT", address.hostAddress ?: target.host, System.currentTimeMillis() - started)
        } catch (e: Throwable) {
            Result(
                false,
                "",
                address.hostAddress ?: target.host,
                System.currentTimeMillis() - started,
                "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
            )
        }
    }

    private fun parseTarget(input: String): Target {
        val value = input.trim()
        require(value.isNotBlank()) { "目标不能为空" }

        if (value.equals("broadcast", true)) {
            return Target("broadcast:${Settings.DEFAULT_PORT}", BROADCAST_HOST, Settings.DEFAULT_PORT, true)
        }
        if (value.startsWith("broadcast:", true)) {
            val port = value.substringAfter(':').toIntOrNull() ?: error("广播端口无效")
            require(port in 1..65535) { "端口范围应为 1–65535" }
            return Target("broadcast:$port", BROADCAST_HOST, port, true)
        }

        // Accept legacy http(s):// URLs, explicit udp:// targets, bare IPs and DNS names.
        // Only host + port are used; any HTTP path/query is discarded during v2 migration.
        var authority = value
        val schemeIndex = authority.indexOf("://")
        if (schemeIndex >= 0) authority = authority.substring(schemeIndex + 3)
        authority = authority.substringBefore('/').substringBefore('?').substringBefore('#').trim()
        require(authority.isNotBlank()) { "地址缺少主机/IP/域名" }

        val (rawHost, port) = parseAuthority(authority)
        val host = normalizeHost(rawHost)
        return Target(formatHostPort(host, port), host, port, false)
    }

    private fun parseAuthority(authority: String): Pair<String, Int> {
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            require(close > 1) { "IPv6 地址格式无效" }
            val host = authority.substring(1, close)
            val tail = authority.substring(close + 1)
            val port = when {
                tail.isBlank() -> Settings.DEFAULT_PORT
                tail.startsWith(":") -> tail.substring(1).toIntOrNull() ?: error("端口无效")
                else -> error("IPv6 地址格式无效")
            }
            require(port in 1..65535) { "端口范围应为 1–65535" }
            return host to port
        }

        val colonCount = authority.count { it == ':' }
        if (colonCount == 0) return authority to Settings.DEFAULT_PORT
        if (colonCount > 1) {
            // Unbracketed IPv6 literal without a port. IPv6 + port must use [addr]:port.
            return authority to Settings.DEFAULT_PORT
        }

        val split = authority.lastIndexOf(':')
        val host = authority.substring(0, split)
        val portText = authority.substring(split + 1)
        require(host.isNotBlank()) { "主机/域名不能为空" }
        val port = portText.toIntOrNull() ?: error("端口无效")
        require(port in 1..65535) { "端口范围应为 1–65535" }
        return host to port
    }

    private fun normalizeHost(host: String): String {
        val clean = host.trim().removeSuffix(".")
        require(clean.isNotBlank()) { "主机/域名不能为空" }
        if (clean.contains(':')) return clean // IPv6 literal
        return runCatching { IDN.toASCII(clean, IDN.ALLOW_UNASSIGNED).lowercase() }
            .getOrElse { clean.lowercase() }
    }

    private fun formatHostPort(host: String, port: Int): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]:$port" else "$host:$port"

    /**
     * Resolve an IP/host/domain target.
     *
     * Address preference:
     *   1. private/link-local IPv4
     *   2. any usable IPv4
     *   3. private/link-local IPv6
     *   4. any usable IPv6
     *
     * Public DNS results are intentionally accepted. This allows DDNS/public domain targets;
     * reachability is still controlled by the receiver's router/NAT/firewall.
     */
    private fun resolve(target: Target): kotlin.Result<InetAddress> = runCatching {
        if (target.broadcast) return@runCatching InetAddress.getByName(BROADCAST_HOST)

        val wifiNetwork = if (Settings.forceWifi) preferredWifiNetwork() else null
        val cacheKey = if (wifiNetwork != null) {
            "wifi:${wifiNetwork.hashCode()}:${target.host}"
        } else {
            "system:${target.host}"
        }
        val now = System.currentTimeMillis()
        val cached = dnsCache[cacheKey]
        if (cached != null && now - cached.resolvedAt <= DNS_CACHE_TTL_MS) {
            return@runCatching cached.address
        }

        val addresses = try {
            resolveAll(target.host, wifiNetwork)
        } catch (e: Throwable) {
            if (cached != null && now - cached.resolvedAt <= DNS_STALE_FALLBACK_MS) {
                Log.w("域名解析失败，暂用缓存 ${target.host} -> ${cached.address.hostAddress}: ${e.message}")
                return@runCatching cached.address
            }
            throw e
        }

        val selected = selectAddress(addresses)
            ?: error("目标 ${target.host} 未解析到可用 IPv4/IPv6 地址")

        dnsCache[cacheKey] = DnsCacheEntry(selected, now)
        Log.d("目标解析 ${target.host} -> ${selected.hostAddress ?: selected.hostName}")
        selected
    }

    private fun resolveAll(host: String, network: Network?): Array<InetAddress> {
        if (network != null) {
            return try {
                // Network.getAllByName(host) uses the DNS resolver attached to that Android Network.
                @Suppress("UNCHECKED_CAST")
                (network.javaClass.getMethod("getAllByName", String::class.java).invoke(network, host) as Array<InetAddress>)
            } catch (e: Throwable) {
                Log.w("Wi-Fi DNS 解析失败，回退系统 DNS: ${e.message}")
                InetAddress.getAllByName(host)
            }
        }
        return InetAddress.getAllByName(host)
    }

    private fun selectAddress(addresses: Array<InetAddress>): InetAddress? {
        val usable = addresses.filterNot { it.isAnyLocalAddress || it.isMulticastAddress }
        return usable.firstOrNull { it is Inet4Address && isLanAddress(it) }
            ?: usable.firstOrNull { it is Inet4Address }
            ?: usable.firstOrNull { it is Inet6Address && isLanAddress(it) }
            ?: usable.firstOrNull { it is Inet6Address }
            ?: usable.firstOrNull()
    }

    private fun createSocketPreferWifi(): DatagramSocket {
        val socket = DatagramSocket()
        if (!Settings.forceWifi || !AppContext.isReady()) return socket
        runCatching {
            val network = preferredWifiNetwork() ?: return@runCatching
            // Network.bindSocket(DatagramSocket) exists on Android API 22+. Reflection keeps
            // this transport easy to static-test against minimal Android stubs.
            network.javaClass
                .getMethod("bindSocket", DatagramSocket::class.java)
                .invoke(network, socket)
        }.onFailure { Log.w("无法把 UDP Socket 绑定到 Wi-Fi，回退系统路由: ${it.message}") }
        return socket
    }

    private fun preferredWifiNetwork(): Network? {
        if (!AppContext.isReady()) return null
        val cm = AppContext.application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun isLanAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) return true
        if (address is Inet4Address) {
            val b = address.address.map { it.toInt() and 0xff }
            return b[0] == 10 ||
                (b[0] == 172 && b[1] in 16..31) ||
                (b[0] == 192 && b[1] == 168) ||
                (b[0] == 100 && b[1] in 64..127)
        }
        if (address is Inet6Address) {
            val first = address.address.first().toInt() and 0xff
            return first and 0xfe == 0xfc
        }
        return false
    }
}
