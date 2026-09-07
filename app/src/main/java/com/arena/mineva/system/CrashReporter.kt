package com.arena.mineva.system

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes every uncaught exception to app storage so a launch crash can be read back
 * by the user or by diagnostics. It does not swallow the crash; it simply persists
 * the stack trace before Android tears the process down.
 */
object CrashReporter {

    private var previous: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (previous != null) return
        appContext = context.applicationContext
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            log(e)
            previous?.uncaughtException(Thread.currentThread(), e)
                ?: kotlin.runCatching { Runtime.getRuntime().exit(1) }
        }
    }

    fun latestCrash(): String? {
        val dir = crashDir() ?: return null
        val f = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return null
        return runCatching { f.readText().take(6000) }.getOrNull()
    }

    fun crashCount(): Int = crashDir()?.listFiles()?.size ?: 0

    private fun crashDir(): File? {
        val ctx = appContext ?: return null
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(base, "crash")
    }

    private fun log(e: Throwable) {
        val dir = crashDir() ?: return
        runCatching {
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            File(dir, "crash_$stamp.log").writeText(
                "time=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n$sw"
            )
        }
    }
}
