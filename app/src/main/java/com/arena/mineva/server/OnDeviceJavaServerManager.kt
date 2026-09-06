package com.arena.mineva.server

import android.content.Context
import com.arena.mineva.AppPrefs
import java.io.File

/**
 * On-device Java Edition Minecraft server manager.
 *
 * A real Java server needs a Java runtime. Android does not ship one, so this manager
 * provisions an app-private directory and, when a portable JRE has been imported, starts
 * the actual server JAR with ProcessBuilder. The manager never pretends Java is already
 * present: it reports exactly which prerequisite is missing (`java`/`JRE` or `server.jar`).
 *
 * This is the healthy on-device path for the user's requirement that the app host a real
 * server on the phone — the runtime is a portable/imported JRE, not an external install
 * outside the app.
 */
class OnDeviceJavaServerManager(private val context: Context) {

    private val dir: File = File(context.filesDir, "ondevice_java_server")
    private val jreDir: File = File(dir, "jre")
    private val logFile: File = File(dir, "server.log")
    private val pidFile: File = File(dir, "server.pid")
    private var process: Process? = null

    fun provision(config: ServerConfig): String {
        dir.mkdirs()
        writeEula()
        writeServerProperties(config)
        writeRunScript()
        return dir.absolutePath
    }

    private fun writeEula() {
        File(dir, "eula.txt").writeText("eula=true\n")
    }

    private fun writeServerProperties(config: ServerConfig) {
        val rconPort = AppPrefs.rconPort
        File(dir, "server.properties").writeText(
            """
            |server-port=${config.port}
            |max-players=${config.maxPlayers}
            |motd=MineAva
            |difficulty=normal
            |gamemode=survival
            |online-mode=false
            |enable-command-block=true
            |enable-rcon=${if (AppPrefs.rconEnabled) "true" else "false"}
            |rcon.port=$rconPort
            |rcon.password=${AppPrefs.rconPassword}
            """.trimMargin()
        )
    }

    private fun writeRunScript() {
        // The launcher deliberately uses the imported `java` inside jre/bin first, so the app
        // is self-contained once the JRE ZIP has been installed. The JVM args are intentionally
        // fixed in the shell script; the actual start() passes the config memory size when it
        // invokes java directly.
        File(dir, "run.sh").writeText(
            """
            |#!/usr/bin/env sh
            |cd "$dir"
            |JRE="$dir/jre/bin/java"
            |if [ -x "${'$'}JRE" ]; then
            |  exec "${'$'}JRE" -Xms512M -Xmx${'$'}MEM_MB M -jar server.jar nogui
            |else
            |  echo "ON-DEVICE JAVA: jre not found. Import a portable ARM JRE ZIP first."
            |  exit 1
            |fi
            """.trimMargin()
        )
    }
    fun serverJar(): File {
        val candidates = listOf("server.jar", "paper.jar", "purpur.jar", "velocity.jar")
        return candidates.map { File(dir, it) }.firstOrNull { it.exists() }
            ?: File(dir, "server.jar")
    }

    fun hasJre(): Boolean = File(jreDir, "bin/java").exists()

    fun hasServerJar(): Boolean = serverJar().exists()

    fun start(config: ServerConfig): String {
        stop()
        provision(config)
        val jar = serverJar()
        val java = File(jreDir, "bin/java")
        return when {
            !java.exists() -> "ON-DEVICE JAVA: JRE نصب نشده. اول بستهٔ JRE (ZIP) را وارد کن."
            !jar.exists() -> "ON-DEVICE JAVA: server.jar نصب نشده. اول JAR سرور را وارد کن."
            else -> {
                logFile.delete()
                runCatching {
                    val cmd = listOf(
                        java.absolutePath,
                        "-Xms512M", "-Xmx${config.memoryMb}M",
                        "-jar", jar.absolutePath,
                        "nogui"
                    )
                    val pb = ProcessBuilder(cmd)
                    pb.directory(dir)
                    pb.redirectErrorStream(true)
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                    process = pb.start()
                    pidFile.writeText("${process!!.pid()}")
                    Thread.sleep(1800L)
                    if (process!!.isAlive) {
                        "ON-DEVICE JAVA: سرور Java روی پورت ${config.port} اجرا شد (PID ${process!!.pid()})."
                    } else {
                        pidFile.delete()
                        val tail = logFile.takeIf { it.exists() }?.readText()?.takeLast(500)
                        "ON-DEVICE JAVA: پردازه خارج شد — ${tail ?: "دلیل نامشخص"}"
                    }
                }.getOrElse { e ->
                    pidFile.delete()
                    "ON-DEVICE JAVA: خطا در اجرا — ${e.message}"
                }
            }
        }
    }

    fun stop(): String {
        val p = process
        if (p != null) {
            runCatching { p.destroy() }
            Thread.sleep(600L)
            if (p.isAlive) runCatching { p.destroyForcibly() }
        }
        process = null
        pidFile.delete()
        return "ON-DEVICE JAVA: سرور متوقف شد."
    }

    fun status(): String {
        val p = process
        if (p != null && p.isAlive) return "ON-DEVICE JAVA: RUNNING (PID ${p.pid()})"
        return statusFromDisk()
    }

    /** Persisted status for the watchdog, works across re-created manager instances. */
    fun statusFromDisk(): String {
        val p = process
        if (p != null && p.isAlive) return "ON-DEVICE JAVA: RUNNING (PID ${p.pid()})"
        if (!pidFile.exists()) {
            val ready = hasJre() && hasServerJar()
            return "ON-DEVICE JAVA: STOPPED" + (if (ready) "" else " — پیش‌نیاز JRE یا server.jar ناقص است.")
        }
        val pid = pidFile.readText().trim().toLongOrNull()
        return if (pid != null && isPidAlive(pid)) {
            "ON-DEVICE JAVA: RUNNING (PID $pid)"
        } else {
            "ON-DEVICE JAVA: CRASHED"
        }
    }

    fun crashAwareRestart(config: ServerConfig): String {
        val wasCrashed = statusFromDisk().contains("CRASHED")
        val result = start(config)
        return if (wasCrashed && result.contains("اجرا شد")) "ON-DEVICE JAVA: after crash, restarted." else result
    }

    private fun isPidAlive(pid: Long): Boolean =
        runCatching { File("/proc/$pid").exists() }.getOrDefault(false)

    fun logs(lines: Int = 80): String {
        if (!logFile.exists()) return "ON-DEVICE JAVA: هنوز لاگی نیست."
        return logFile.readLines().takeLast(lines).joinToString("\n")
    }

    fun restart(config: ServerConfig): String {
        stop()
        return start(config)
    }
}
