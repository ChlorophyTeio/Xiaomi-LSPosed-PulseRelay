package website.xihan.pbra

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.ThreadLocalRandom

/**
 * Tiny UDP transport used by PulseRelay.
 *
 * Protocol (UTF-8 text):
 *   PULSE/1|HR|<seq>|<bpm>|<unix_ms>
 *   PULSE/1|PING|<nonce>
 *   PULSE/1|PONG|<nonce>
 *
 * Real-time HR packets are fire-and-forget (latest value wins). PING is only used by the
 * settings screen so the user can verify that a receiver is actually listening.
 */
object PulseUdp {
    private const val MAGIC = "PULSE/1"
    private const val BROADCAST_HOST = "255.255.255.255"

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
                        // For broadcast, keep collecting replies until timeout so the UI can show receiver count.
                        it.soTimeout = (deadline - System.currentTimeMillis()).coerceAtLeast(1L).toInt()
                    }
                }

                if (responders.isEmpty()) {
                    Result(false, "PING timeout", address.hostAddress ?: target.host,
                        System.currentTimeMillis() - started, "未收到 PONG")
                } else {
                    val status = if (target.broadcast) "PONG x${responders.size}" else "PONG"
                    Result(true, status, responders.joinToString(","), System.currentTimeMillis() - started)
                }
            }
        } catch (e: Throwable) {
            Result(false, "", address.hostAddress ?: target.host, System.currentTimeMillis() - started,
                "${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
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
            Result(false, "", address.hostAddress ?: target.host, System.currentTimeMillis() - started,
                "${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    private fun parseTarget(input: String): Target {
        var value = input.trim()
        require(value.isNotBlank()) { "目标不能为空" }

        // Seamless migration from v2.x HTTP addresses.
        if (value.startsWith("http://", true) || value.startsWith("https://", true) || value.startsWith("udp://", true)) {
            val uri = URI(value)
            val host = uri.host ?: error("地址缺少主机/IP")
            val port = if (uri.port > 0) uri.port else Settings.DEFAULT_PORT
            return Target(formatHostPort(host, port), host, port, false)
        }

        if (value.equals("broadcast", true)) {
            return Target("broadcast:${Settings.DEFAULT_PORT}", BROADCAST_HOST, Settings.DEFAULT_PORT, true)
        }
        if (value.startsWith("broadcast:", true)) {
            val port = value.substringAfter(':').toIntOrNull() ?: error("广播端口无效")
            require(port in 1..65535) { "端口范围应为 1–65535" }
            return Target("broadcast:$port", BROADCAST_HOST, port, true)
        }

        val uri = URI("udp://$value")
        val host = uri.host ?: error("地址格式应为 IP、主机名 或 IP:端口")
        val port = if (uri.port > 0) uri.port else Settings.DEFAULT_PORT
        require(port in 1..65535) { "端口范围应为 1–65535" }
        return Target(formatHostPort(host, port), host, port, false)
    }

    private fun formatHostPort(host: String, port: Int): String =
        if (host.contains(':') && !host.startsWith("[")) "[$host]:$port" else "$host:$port"

    private fun resolve(target: Target): kotlin.Result<InetAddress> = runCatching {
        if (target.broadcast) return@runCatching InetAddress.getByName(BROADCAST_HOST)
        val addresses = InetAddress.getAllByName(target.host)
        addresses.firstOrNull(::isLanAddress)
            ?: error("目标 ${target.host} 未解析到局域网地址")
    }

    private fun createSocketPreferWifi(): DatagramSocket {
        val socket = DatagramSocket()
        if (!Settings.forceWifi || !AppContext.isReady()) return socket
        runCatching {
            val cm = AppContext.application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network: Network = cm.allNetworks.firstOrNull { n ->
                cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } ?: return@runCatching

            // Network.bindSocket(DatagramSocket) exists on Android API 22+. Use reflection here so
            // the transport stays easy to compile against minimal test stubs as well.
            network.javaClass
                .getMethod("bindSocket", DatagramSocket::class.java)
                .invoke(network, socket)
        }.onFailure { Log.w("无法把 UDP Socket 绑定到 Wi-Fi，回退系统路由: ${it.message}") }
        return socket
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
