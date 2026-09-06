package com.arena.mineva.server

import android.content.Context
import java.io.File

/**
 * Best-effort on-device Minecraft server manager.
 *
 * On Android the app cannot ship a full Java runtime, but a Bedrock dedicated server is a
 * single native process. This manager provisions the app-private directory, writes
 * server.properties, and starts the server process with ProcessBuilder while capturing
 * its log into the app's files. Whether the downloaded binary actually runs depends on the
 * phone architecture (arm64) and whether Mojang publishes a compatible build; the manager
 * reports the failure accurately.
 */
class OnDeviceServerManager(private val context: Context) {

    private val dir: File = File(context.filesDir, "ondevice_server")
    private val logFile: File = File(dir, "server.log")
    private var process: Process? = null
    private var lastStartError: String? = null

    fun provision(config: ServerConfig): String {
        dir.mkdirs()
        writeServerProperties(config)
        // The app uses sh to run a launcher script rather than requiring exec permissions.
        val script = File(dir, "run.sh")
        script.writeText(
            """
            |#!/usr/bin/env sh
            |cd "$dir"
            |if [ -x "./bedrock_server" ]; then
            |  LD_LIBRARY_PATH=. ./bedrock_server
            |else
            |  echo "ON-DEVICE: bedrock_server not found. Download it through the app or place it manually."
            |fi
            """.trimMargin()
        )
        return dir.absolutePath
    }

    private fun writeServerProperties(config: ServerConfig) {
        val props = File(dir, "server.properties")
        props.writeText(
            """
            |server-name=MineAva
            |server-port=${config.port}
            |max-players=${config.maxPlayers}
            |difficulty=normal
            |gamemode=survival
            |allow-cheats=true
            """.trimMargin()
        )
    }

    fun start(config: ServerConfig): String {
        stop()
        provision(config)
        val script = File(dir, "run.sh")
        logFile.delete()
        return runCatching {
            val pb = ProcessBuilder("sh", script.absolutePath)
            pb.directory(dir)
            pb.redirectErrorStream(true)
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            process = pb.start()
            // Report that the process started; actual server readiness is checked via logs.
            Thread.sleep(1500L)
            if (!process!!.isAlive) {
                lastStartError = logFile.takeIf { it.exists() }?.readText()?.takeLast(300)
            }
            if (process!!.isAlive) "ON-DEVICE: process started on port ${config.port}." else {
                "ON-DEVICE: process exited — ${lastStartError ?: "unknown reason"}"
            }
        }.getOrElse { e ->
            lastStartError = e.message
            "ON-DEVICE: could not start — ${e.message}"
        }
    }

    fun stop(): String {
        val p = process
        if (p == null) return "ON-DEVICE: no running process."
        runCatching { p.destroy() }
        Thread.sleep(500L)
        if (p.isAlive) {
            runCatching { p.destroyForcibly() }
        }
        process = null
        return "ON-DEVICE: server stopped."
    }

    fun status(): String {
        val p = process
        return if (p != null && p.isAlive) "ON-DEVICE: RUNNING (PID ${p.pid()})" else "ON-DEVICE: STOPPED"
    }

    fun logs(lines: Int = 80): String {
        if (!logFile.exists()) return "ON-DEVICE: no log yet."
        return logFile.readLines().takeLast(lines).joinToString("\n")
    }

    fun restart(config: ServerConfig): String {
        stop()
        return start(config)
    }
}
