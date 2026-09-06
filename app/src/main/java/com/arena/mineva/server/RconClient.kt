package com.arena.mineva.server

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * Minimal Source RCON client.
 *
 * Minecraft servers (both Bedrock and Java, when `enable-rcon=true`) expose a TCP RCON
 * channel. This is the real, supported way to execute server commands from the app —
 * much stronger than "copy to clipboard". It is used by the professional panel and the
 * on-device Java/Bedrock managers.
 *
 * Protocol: little-endian int32 length, int32 request id, int32 type, null-terminated
 * UTF-8 payload, trailing null byte.
 */
class RconClient {

    data class RconResponse(val success: Boolean, val message: String = "", val authenticated: Boolean = false)

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var id = 0
    private var authenticated = false

    fun connect(host: String, port: Int, password: String, timeoutMs: Int = 8000): RconResponse {
        return runCatching {
            socket?.close()
            val s = Socket()
            s.connect(java.net.InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
            socket = s
            input = DataInputStream(s.getInputStream())
            output = DataOutputStream(s.getOutputStream())

            val auth = sendPacket(3, password)
            authenticated = auth.type != 3 && auth.id == id && auth.body.isNotEmpty()
            if (authenticated) RconResponse(true, auth.body, true)
            else RconResponse(false, if (auth.body.isNotBlank()) auth.body else "احراز هویت RCON ناموفق بود.", false)
        }.getOrElse { e ->
            RconResponse(false, "اتصال RCON ناموفق بود: ${e.message ?: "نامشخص"}")
        }
    }

    fun command(command: String): RconResponse {
        if (!authenticated || socket?.isConnected != true) {
            return RconResponse(false, "ابتدا به RCON متصل شو.")
        }
        return runCatching {
            val p = sendPacket(2, command)
            if (p.type == 0 || p.type == 2) RconResponse(true, p.body)
            else RconResponse(false, "پاسخ غیرمنتظره از RCON (type=${p.type}).")
        }.getOrElse { e ->
            RconResponse(false, "ارسال به RCON ناموفق بود: ${e.message ?: "نامشخص"}")
        }
    }

    fun isConnected(): Boolean = socket?.isConnected == true && !socket!!.isClosed

    fun disconnect() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
        authenticated = false
    }

    private data class Packet(val id: Int, val type: Int, val body: String)

    private fun sendPacket(type: Int, body: String): Packet {
        val payload = body.toByteArray(Charsets.UTF_8)
        val bodyWithNulls = payload.size + 2
        val packetLength = 4 + 4 + bodyWithNulls
        id++

        val out = output ?: error("Not connected")
        out.writeInt(packetLength)
        out.writeInt(id)
        out.writeInt(type)
        out.write(payload)
        out.write(0)
        out.write(0)
        out.flush()

        return readResponse(id)
    }

    private fun readResponse(expectedId: Int): Packet {
        val inp = input ?: error("Not connected")
        while (true) {
            val length = inp.readInt()
            if (length < 10 || length > 8192) error("Invalid RCON length $length")
            val reqId = inp.readInt()
            val type = inp.readInt()
            val bodyBytes = ByteArray(length - 10)
            inp.readFully(bodyBytes)
            // trailing two null bytes
            inp.readByte()
            inp.readByte()
            val body = String(bodyBytes, Charsets.UTF_8).trimEnd('\u0000').trim()
            if (reqId == expectedId && type != 3) return Packet(reqId, type, body)
            // Type 3 is a login response; it is expected on auth with the same request id.
            return Packet(reqId, type, body)
        }
    }
}
