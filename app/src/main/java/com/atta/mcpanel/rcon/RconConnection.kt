package com.atta.mcpanel.rcon

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/** رویدادهای اتصال RCON که در نخِ اصلی UI تحویل داده می‌شوند. */
sealed class RconEvent {
    object Connecting : RconEvent()
    data class Auth(val ok: Boolean, val message: String) : RconEvent()
    data class Output(val text: String) : RconEvent()
    data class Closed(val reason: String) : RconEvent()
}

/**
 * کلاینت سبکِ RCON (پروتکل کنسول سرور ماینکرفت).
 * برای اجرای دستورهای اپراتور روی سرورِ شخصی، بیرون از بازی.
 */
class RconConnection(
    private val host: String,
    private val port: Int,
    private val password: String,
    private val onEvent: (RconEvent) -> Unit,
    connectTimeoutMs: Int = 6000,
) {
    var isOpen: Boolean = false
        private set

    private val nextId = AtomicInteger(0)
    private val writeLock = Any()
    private val timeoutMs = connectTimeoutMs

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var socket: Socket? = null

    private var output: DataOutputStream? = null

    private var closedByUser = false

    fun start() {
        if (worker?.isAlive == true) return
        closedByUser = false
        isOpen = false
        val t = Thread({ runWorker() }, "atta-rcon")
        worker = t
        t.start()
    }

    /** یک دستور را به کنسول سرور می‌فرستد. */
    fun send(command: String): Boolean {
        val sock = socket ?: return false
        if (!isOpen) return false
        val cmd = command.trim().removePrefix("/")
        if (cmd.isEmpty()) return true
        val id = nextId.incrementAndGet()
        return try {
            synchronized(writeLock) {
                val o = output ?: return false
                writePacket(o, id, 2, cmd)
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    /** بستن دستی اتصال. */
    fun stop() {
        closedByUser = true
        runCatching { socket?.close() }
        worker?.interrupt()
    }

    // ------------------------------------------------------------------ //

    private fun runWorker() {
        onEvent(RconEvent.Connecting)
        val sock = Socket()
        socket = sock
        try {
            sock.connect(InetSocketAddress(host, port), timeoutMs)
            sock.tcpNoDelay = true
            val input = DataInputStream(sock.getInputStream())
            val out = DataOutputStream(sock.getOutputStream())
            output = out

            // احراز هویت
            val authId = nextId.incrementAndGet()
            synchronized(writeLock) { writePacket(out, authId, 3, password) }

            while (!Thread.currentThread().isInterrupted) {
                val p = readPacket(input) ?: break
                when {
                    p.id == authId -> {
                        isOpen = true
                        onEvent(RconEvent.Auth(true, "اتصال برقرار شد"))
                    }
                    p.id == -1 -> {
                        val msg = p.text.ifBlank { "رمز عبور نادرست است" }
                        onEvent(RconEvent.Auth(false, msg))
                        sock.close()
                        return
                    }
                    p.id > 0 && p.type == 0 && p.text.isNotBlank() ->
                        onEvent(RconEvent.Output(p.text))
                }
            }
            onEvent(RconEvent.Closed(if (closedByUser) "قطع شد" else "اتصال بسته شد"))
        } catch (e: Exception) {
            if (!closedByUser) {
                val reason = when (e) {
                    is IOException -> "اتصال قطع شد (${e.message ?: ""})".trimEnd()
                    else -> "خطا: ${e.message ?: "نامشخص"}"
                }
                onEvent(RconEvent.Closed(reason))
            }
        } finally {
            isOpen = false
            output = null
            socket = null
            runCatching { sock.close() }
        }
    }

    // ------------------------------------------------------------------ //

    private class Packet(val id: Int, val type: Int, val text: String)

    private fun writePacket(o: DataOutputStream, id: Int, type: Int, body: String) {
        val payload = body.toByteArray(Charsets.UTF_8)
        val length = 4 + 4 + payload.size + 2
        writeIntLE(o, length)
        writeIntLE(o, id)
        writeIntLE(o, type)
        o.write(payload)
        o.writeByte(0)
        o.writeByte(0)
        o.flush()
    }

    private fun readPacket(i: DataInputStream): Packet? {
        val length = readIntLE(i) ?: return null
        if (length < 10) throw IOException("پاکت RCON نامعتبر")
        val id = readIntLE(i) ?: throw IOException("اتصال ناقص")
        val type = readIntLE(i) ?: throw IOException("اتصال ناقص")
        val payloadBytes = length - 10
        val buf = ByteArray(payloadBytes)
        i.readFully(buf)
        val text = String(buf, Charsets.UTF_8).trimEnd('\u0000').trim()
        return Packet(id, type, text)
    }

    private fun writeIntLE(o: DataOutputStream, v: Int) {
        o.writeByte(v and 0xFF)
        o.writeByte((v shr 8) and 0xFF)
        o.writeByte((v shr 16) and 0xFF)
        o.writeByte((v shr 24) and 0xFF)
    }

    private fun readIntLE(i: DataInputStream): Int? {
        return try {
            var v = 0
            for (k in 0..3) v = v or (i.readUnsignedByte() shl (8 * k))
            v
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        /** تست سریع اتصال (برای دکمهٔ «تست» در تنظیمات). */
        fun testConnection(
            host: String,
            port: Int,
            password: String,
            onResult: (ok: Boolean, message: String) -> Unit,
        ) {
            var done = false
            val conn = RconConnection(host, port, password, { ev ->
                when (ev) {
                    is RconEvent.Auth -> {
                        if (!done) {
                            done = true
                            onResult(ev.ok, if (ev.ok) "اتصال RCON برقرار شد" else "ورود رد شد: ${ev.message}")
                            conn.stop()
                        }
                    }
                    is RconEvent.Closed -> if (!done) {
                        done = true
                        onResult(false, ev.reason)
                    }
                    else -> Unit
                }
            }, connectTimeoutMs = 5000)
            conn.start()
        }
    }
}
