package com.arena.mineva.server

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Small SSH/SFTP wrapper backed by JSch. Used by the real VPS provisioner.
 */
class SshClient {

    data class ExecResult(val exitCode: Int, val output: String)

    fun connect(
        host: String,
        user: String,
        password: String?,
        keyPath: String?,
        keyPassphrase: String? = null,
        port: Int = 22,
        timeoutMs: Int = 30_000
    ): Session {
        if (host.isBlank() || user.isBlank()) {
            throw IllegalArgumentException("آدرس سرور و نام کاربری SSH لازم است.")
        }

        val jsch = JSch()
        if (!keyPath.isNullOrBlank() && File(keyPath).exists()) {
            if (!keyPassphrase.isNullOrBlank()) {
                jsch.addIdentity(keyPath, keyPassphrase)
            } else {
                jsch.addIdentity(keyPath)
            }
        }

        @Suppress("DEPRECATION")
        val session = jsch.getSession(user, host, port)
        if (!password.isNullOrBlank()) {
            session.setPassword(password)
        }

        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
        session.setServerAliveInterval(15_000)
        session.connect(timeoutMs)
        return session
    }

    fun exec(session: Session, command: String, timeoutMs: Int = 600_000): ExecResult {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)

        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        channel.setInputStream(null)
        channel.outputStream = null
        channel.setErrStream(stderr)

        channel.connect(timeoutMs)
        copyStream(channel.inputStream, stdout)

        // Give the process a short grace period to flush stderr.
        var i = 0
        while (!channel.isClosed && i < 20) {
            Thread.sleep(100)
            i++
        }
        val code = channel.exitStatus
        channel.disconnect()

        val out = stdout.toString(StandardCharsets.UTF_8.name())
        val err = stderr.toString(StandardCharsets.UTF_8.name())
        return ExecResult(code ?: -1, (out + "\n" + err).trim())
    }

    fun put(session: Session, content: String, remotePath: String, timeoutMs: Int = 30_000) {
        putBytes(session, content.toByteArray(StandardCharsets.UTF_8), remotePath, timeoutMs)
    }

    fun putBytes(session: Session, bytes: ByteArray, remotePath: String, timeoutMs: Int = 30_000) {
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(timeoutMs)
        channel.put(ByteArrayInputStream(bytes), remotePath)
        channel.disconnect()
    }

    private fun copyStream(input: InputStream, out: ByteArrayOutputStream) {
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
    }
}
