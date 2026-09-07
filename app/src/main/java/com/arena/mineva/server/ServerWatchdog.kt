package com.arena.mineva.server

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash-detection + auto-restart watchdog.
 *
 * It periodically checks the active server:
 *  - local Bedrock / Java: reads the persisted PID/status and restarts if the process died.
 *  - VPS: asks systemd/systemctl over SSH and restarts with the tmux launcher if needed.
 *
 * Events are appended to `filesDir/server_watchdog.log`.
 */
class ServerWatchdog(private val context: Context) {

    private val bedrock = OnDeviceServerManager(context)
    private val java = OnDeviceJavaServerManager(context)

    private val logFile: File = File(context.filesDir, "server_watchdog.log")

    data class WatchResult(
        val status: String,
        val crashed: Boolean,
        val restarted: Boolean,
        val message: String
    )

    fun checkOnce(
        config: ServerConfig,
        autoRestart: Boolean,
        sshPassword: String? = null,
        sshKeyPass: String? = null
    ): WatchResult {
        return when (config.target) {
            ServerTarget.VPS -> checkVps(config, autoRestart, sshPassword, sshKeyPass)
            ServerTarget.LOCAL -> checkLocal(config, autoRestart)
        }
    }

    private fun checkLocal(config: ServerConfig, autoRestart: Boolean): WatchResult {
        val wantsJava = config.edition == ServerEdition.JAVA || config.edition == ServerEdition.HYBRID
        val wantsBedrock = config.edition == ServerEdition.BEDROCK || config.edition == ServerEdition.HYBRID

        var status = "STOPPED"
        when {
            wantsJava -> status = java.statusFromDisk()
            wantsBedrock -> status = bedrock.statusFromDisk()
        }

        val crashed = status.contains("CRASHED")
        val stopped = status.contains("STOPPED")

        if (crashed && autoRestart) {
            val r = if (wantsJava) java.crashAwareRestart(config) else bedrock.crashAwareRestart(config)
            val ok = r.contains("started") || r.contains("اجرا شد")
            append("CRASH detected -> ${if (ok) "restarted" else "restart failed"}: ${r.take(200)}")
            return WatchResult(status, true, ok, r)
        }
        if (stopped && autoRestart) {
            val r = if (wantsJava) java.start(config) else bedrock.start(config)
            val ok = r.contains("started") || r.contains("اجرا شد") || !r.contains("خطا")
            append("STOPPED detected -> attempted start: ${r.take(200)}")
            return WatchResult(status, false, ok, r)
        }
        return WatchResult(status, crashed, false, status)
    }

    private fun checkVps(
        config: ServerConfig,
        autoRestart: Boolean,
        password: String?,
        keyPass: String?
    ): WatchResult {
        if (config.host.isBlank()) {
            append("No VPS host configured")
            return WatchResult("NO-HOST", false, false, "آدرس VPS در پروفایل نیست.")
        }
        return try {
            val ssh = SshClient()
            val session = ssh.connect(
                host = config.host,
                user = config.user,
                password = password?.ifBlank { null },
                keyPath = config.sshKeyPath.ifBlank { null },
                keyPassphrase = keyPass?.ifBlank { null },
                port = config.sshPort
            )
            val active = ssh.exec(session, "systemctl is-active MineAva-server 2>/dev/null || echo inactive").output.trim()
            val tmux = ssh.exec(session, "tmux has-session -t MineAvaServer 2>/dev/null && echo yes || echo no").output.trim()
            session.disconnect()

            val down = active == "inactive" || active == "failed" || tmux == "no"
            if (down && autoRestart) {
                val r = vpsRunSudo(
                    config,
                    "systemctl restart MineAva-server; cd ~/minecraft-server && tmux new-session -d -s MineAvaServer ./run.sh",
                    password,
                    keyPass
                )
                append("VPS DOWN detected -> restart: ${r.take(200)}")
                WatchResult(active, true, true, r)
            } else {
                WatchResult(if (down) "DOWN ($active)" else active, down, false, "سالم: $active / tmux=$tmux")
            }
        } catch (e: Exception) {
            val msg = "خطا: ${e.message}"
            append(msg)
            WatchResult("ERROR", false, false, msg)
        }
    }

    private fun vpsRunSudo(config: ServerConfig, command: String, password: String?, keyPass: String?): String {
        val ssh = SshClient()
        val session = ssh.connect(
            host = config.host,
            user = config.user,
            password = password?.ifBlank { null },
            keyPath = config.sshKeyPath.ifBlank { null },
            keyPassphrase = keyPass?.ifBlank { null },
            port = config.sshPort
        )
        val uidResult = ssh.exec(session, "id -u").output.trim()
        val finalCommand = when {
            uidResult == "0" -> command
            password != null && password.isNotBlank() -> {
                val esc = password.replace("'", "'\\''")
                val escCmd = command.replace("\"", "\\\"")
                "printf '%s\\n' '$esc' | sudo -S -p '' bash -c \"$escCmd\" 2>&1"
            }
            else -> command
        }
        val out = ssh.exec(session, finalCommand).output
        session.disconnect()
        return out
    }

    fun logs(lines: Int = 80): String {
        if (!logFile.exists()) return "هنوز رویدادی ثبت نشده است."
        return logFile.readLines().takeLast(lines).joinToString("\n")
    }

    fun clearLog() {
        logFile.delete()
    }

    private fun append(text: String) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        runCatching {
            logFile.parentFile?.mkdirs()
            logFile.appendText("[$time] $text\n")
        }
    }
}
